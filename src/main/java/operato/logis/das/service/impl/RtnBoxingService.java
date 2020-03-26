package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxItem;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.service.api.IAssortService;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 박스 처리 서비스
 * 
 * @author shortstop
 */
@Component("rtnBoxingService")
public class RtnBoxingService extends AbstractExecutionService implements IBoxingService {

	@Override
	public String getJobType() {
		return LogisConstants.JOB_TYPE_RTN;
	}

	@Override
	public JobConfigSet getJobConfigSet(String batchId) {
		return BatchJobConfigUtil.getConfigSetService().getConfigSet(batchId);
	}

	@Override
	public Object assignBoxToCell(JobBatch batch, String cellCd, String boxId, Object... params) {
		// 1. Box 사용 여부 체크
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		String boxIdUniqueScope = BatchJobConfigUtil.getBoxIdUniqueScope(batch, LogisConstants.BOX_ID_UNIQUE_SCOPE_GLOBAL);
		
		switch(boxIdUniqueScope) {
			case LogisConstants.BOX_ID_UNIQUE_SCOPE_GLOBAL :
				condition.addFilter("boxId", boxId);
				break;
				
			case LogisConstants.BOX_ID_UNIQUE_SCOPE_DAY :
				condition.addFilter("jobDate", batch.getJobDate());
				condition.addFilter("boxId", boxId);
				break;
				
			case LogisConstants.BOX_ID_UNIQUE_SCOPE_BATCH :
				condition.addFilter("batchId", batch.getId());
				condition.addFilter("boxId", boxId);
				break;
		}
		
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
		if(boxPack != null) {
			throw new ElidomRuntimeException("박스 ID [" + boxId + "]는 이미 사용한 박스입니다.");
		} 
				
		// 2. 작업 WorkCell 조회
		condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("cellCd", cellCd);
		WorkCell cell = this.queryManager.selectByCondition(WorkCell.class, condition);
		cell.setBoxId(boxId);
		
		// 3. 작업 인스턴스 정보 조회
		condition.removeFilter("cellCd");
		condition.addSelect("id", "picked_qty", "picking_qty");
		condition.addFilter("subEquipCd", cellCd); 
		condition.addFilter("status", SysConstants.IN, LogisConstants.JOB_STATUS_PF);
		JobInstance job = this.queryManager.selectByCondition(JobInstance.class, condition);
		
		// 4. 작업 인스턴스에 피킹 진행 중인 수량이 있다면 Fullbox 안 됨 
		if(job.getPickingQty() > 0) {
			throw new ElidomRuntimeException("완료되지 않은 작업이 있습니다.");
		}
		
		// 5. 클라이언트에 할당 정보 리턴
		return ValueUtil.newMap("detail,job_id,picked_qty,picking_qty", cell, job.getId(), job.getPickedQty(), job.getPickingQty());
	}

	@Override
	public Object resetBoxToCell(JobBatch batch, String cellCd, Object... params) {
		// 작업 WorkCell 조회 후 BoxId를 클리어 
		WorkCell cell = AnyEntityUtil.findEntityBy(batch.getDomainId(), true, WorkCell.class, "domainId,batchId,cellCd", batch.getDomainId(), batch.getId(), cellCd);
		cell.setBoxId(null);
		this.queryManager.update(cell, "boxId", "updatedAt");
		return cell;
	}

	@Override
	public BoxPack fullBoxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Object... params) {
		int resQty = jobList.get(0).getPickedQty();
		return this.partialFullboxing(batch, workCell, jobList, resQty, params);
	}

	@Override
	public BoxPack partialFullboxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Integer fullboxQty, Object... params) {
		JobInstance job = jobList.get(0); 
		JobInstance boxingJob = null;
		
		// 1. 예정 수량이 확정 수량보다 크다면 작업 데이터 분리 
		if((job.getPickQty() - fullboxQty) > 0) {
			IAssortService assortSvc = (IAssortService)params[2];
			boxingJob = assortSvc.splitJob(job, null, fullboxQty);
			
		// 2. 그렇지 않으면 작업 데이터 자체로 그대로 처리
		} else {
			job.setPickedQty(fullboxQty); 
			String nowStr = DateUtil.currentTimeStr();
			job.setBoxId(workCell.getBoxId());
			job.setBoxedAt(nowStr);
			job.setPickEndedAt(nowStr);
			job.setStatus(LogisConstants.JOB_STATUS_BOXED); 
			this.queryManager.update(job, "pickedQty", "boxedAt", "pickEndedAt", "status", "updatedAt");
			boxingJob = job;
		} 
		
		// 3. 박스 정보 생성
		BoxPack boxPack = this.createNewBoxPack(batch, boxingJob, workCell);
		
		// 4. 박스 내품 생성
		this.generateBoxItemsBy(boxPack, boxingJob, fullboxQty);	 
		
		// 5. 박스 리턴
		return boxPack;
	}

	@Override
	public List<BoxPack> batchBoxing(JobBatch batch) {
		// 1. 배치 내 상태가 '피킹 시작' or '피킹 완료' 인 작업 데이터를 셀 별로 모두 조회  
		String sql = "select * from job_instances where domain_id = :domainId and batch_id = :batchId and status in :statuses and picked_qty > 0";
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,statuses", batch.getDomainId(), batch.getId(), LogisConstants.JOB_STATUS_PF);
		List<JobInstance> jobs = this.queryManager.selectListBySql(sql, params, JobInstance.class, 0, 0);
		
		// 2. 작업 데이터가 있는 셀 별로 모두 Fullbox 호출 ...
		List<BoxPack> boxPacks = new ArrayList<BoxPack>();
		
		for(JobInstance job : jobs) {
			WorkCell workCell = AnyEntityUtil.findEntityBy(job.getDomainId(), true, true, WorkCell.class, "id,cell_cd,ind_cd,job_type,com_cd,sku_cd,box_id,status,job_instance_id", "domainId,batchId,cellCd", job.getDomainId(), job.getBatchId(), job.getSubEquipCd());
			boxPacks.add(this.fullBoxing(batch, workCell, ValueUtil.toList(job), job.getPickedQty()));
		}
		
		// 3. 결과 리턴 
		return boxPacks;
	}

	@Override
	public BoxPack cancelFullboxing(BoxPack box) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * BoxPack 생성
	 * 
	 * @param batch
	 * @param job
	 * @param cell
	 * @return
	 */
	private BoxPack createNewBoxPack(JobBatch batch, JobInstance job, WorkCell cell) {
		BoxPack boxPack = ValueUtil.populate(batch, new BoxPack());
		ValueUtil.populate(job, boxPack);
		boxPack.setId(null);
		boxPack.setStatus(BoxPack.BOX_STATUS_BOXED);
		this.queryManager.insert(boxPack);
		return boxPack;
	}

	/**
	 * 작업 정보 기준으로 BoxItem 생성
	 *
	 * @param boxPack
	 * @param job
	 * @param totalPickedQty
	 */
	public void generateBoxItemsBy(BoxPack boxPack, JobInstance job, int totalPickedQty) {
		// 1. 주문 정보 조회 
		String sql = "select * from orders where domain_id = :domainId and batch_id = :batchId and sku_cd = :skuCd and picked_qty > boxed_qty order by order_no asc, order_qty desc";
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,skuCd", job.getDomainId(), job.getBatchId(), job.getSkuCd());
		List<Order> sources = this.queryManager.selectListBySql(sql, params, Order.class, 0, 0);
 		String boxId = job.getBoxId(); 
 		
 		// 2. 박스 내품 내역 
 		List<BoxItem> boxItems = new ArrayList<BoxItem>(2);
 		
 		// 2. 주문에 피킹 확정 수량 업데이트
		for(Order source : sources) { 
			if(totalPickedQty > 0) {
				int pickedQty = source.getPickedQty();
				int boxedQty = source.getBoxedQty();
				int remainQty = pickedQty - boxedQty;
				
				// 2-1. 박싱 처리 수량 업데이트 및 주문 라인 분류 종료
				if(totalPickedQty >= remainQty) {
					source.setBoxId(boxId);
					source.setBoxedQty(source.getBoxedQty() + remainQty);
					boxItems.add(this.newBoxItemBy(boxPack, job, source, remainQty));
					totalPickedQty = totalPickedQty - remainQty;
					
				// 2-2. 박싱 처리 수량 업데이트
				} else if(remainQty > totalPickedQty) {
					source.setBoxId(boxId);
					source.setBoxedQty(source.getBoxedQty() + totalPickedQty);
					boxItems.add(this.newBoxItemBy(boxPack, job, source, totalPickedQty));
					totalPickedQty = 0;
				}
				
				this.queryManager.update(source, "boxId", "boxedQty", "updatedAt");
				
			} else {
				break;
			}
		};
		
		// 3. 박스 내품 내역 생성
		AnyOrmUtil.insertBatch(boxItems, 100);
	}
	
	/**
	 * 박스 내품 내역 생성
	 * 
	 * @param boxPack
	 * @param job
	 * @param order
	 * @param boxedQty
	 * @return
	 */
	public BoxItem newBoxItemBy(BoxPack boxPack, JobInstance job, Order order, int boxedQty) {
		BoxItem item = new BoxItem();
		item.setBoxPackId(boxPack.getId());
		item.setDomainId(order.getDomainId());
		item.setOrderId(order.getId());
		item.setOrderNo(order.getOrderNo());
		item.setOrderLineNo(order.getOrderLineNo());
		item.setOrderDetailId(order.getOrderDetailId());
		item.setComCd(order.getComCd());
		item.setShopCd(order.getShopCd());
		item.setSkuCd(order.getSkuCd());
		item.setSkuNm(order.getSkuNm());
		item.setPackType(order.getPackType());
		item.setPickedQty(boxedQty);
		return item;
	}

}
