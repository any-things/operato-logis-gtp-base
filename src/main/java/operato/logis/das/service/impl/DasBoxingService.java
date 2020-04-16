package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxItem;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.box.UndoBoxingEvent;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 출고용 박스 처리 서비스
 * 
 * @author shortstop
 */
@Component("dasBoxingService")
public class DasBoxingService extends AbstractExecutionService implements IBoxingService {

	@Override
	public String getJobType() {
		return LogisConstants.JOB_TYPE_DAS;
	}

	@Override
	public JobConfigSet getJobConfigSet(String batchId) {
		return BatchJobConfigUtil.getConfigSetService().getConfigSet(batchId);
	}
	
	@Override
	public boolean isUsedBoxId(JobBatch batch, String boxId, boolean exceptionWhenBoxIdUsed) {
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
		if(boxPack != null && exceptionWhenBoxIdUsed) {
			throw new ElidomRuntimeException("박스 ID [" + boxId + "]는 이미 사용한 박스입니다.");
		}
		
		return boxPack != null;
	}

	@Override
	public Object assignBoxToCell(JobBatch batch, String cellCd, String boxId, Object... params) {
		// 1. Box 사용 여부 체크
		this.isUsedBoxId(batch, boxId, true);
				
		// 2. 작업 WorkCell 조회
		Long domainId = batch.getDomainId();
		WorkCell workCell = AnyEntityUtil.findEntityBy(domainId, true, WorkCell.class, null, "domainId,batchId,cellCd", domainId, batch.getId(), cellCd);
		workCell.setBoxId(boxId);
		
		// 3. 셀에 처리할 작업 인스턴스 정보 조회
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addSelect("id");
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("subEquipCd", cellCd);
		condition.addFilter("status", LogisConstants.JOB_STATUS_WAIT);
		JobInstance job = this.queryManager.selectByCondition(JobInstance.class, condition);
		
		// 4. 작업 인스턴스에 피킹 진행 중인 수량이 있다면 박스 매핑 안 됨. 
		if(job == null) {
			// 셀에 박싱 처리할 작업이 없습니다.
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_FOR_BOXING");
		}
		
		// 5. 클라이언트에 할당 정보 리턴
		return workCell;
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
		// 1. 작업 리스트 존재 여부 체크
		if(ValueUtil.isEmpty(jobList)) {
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_FOR_BOXING");
		}
		String nowStr = DateUtil.currentTimeStr();
		
		// 2. 작업 정보 업데이트 
		for(JobInstance job : jobList) {
			job.setBoxId(workCell.getBoxId());
			job.setBoxedAt(nowStr);
			if(ValueUtil.isEmpty(job.getPickEndedAt())) {
				job.setPickEndedAt(nowStr);
			}
			job.setStatus(LogisConstants.JOB_STATUS_BOXED);
		}
		this.queryManager.updateBatch(jobList, "boxId", "boxedAt", "pickEndedAt", "status", "updatedAt");
		
		// 3. 박스 정보 생성
		BoxPack boxPack = this.createNewBoxPack(batch, jobList, workCell);
		
		// 4. 박스 내품 생성
		this.generateBoxItemsBy(boxPack, jobList);
		
		// 5. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack partialFullboxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Integer fullboxQty, Object... params) {
		throw ThrowUtil.newNotSupportedMethod();
	}

	@Override
	public List<BoxPack> batchBoxing(JobBatch batch) {		
		// 1. 작업 셀 리스트 조회
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("status", SysConstants.IN, LogisConstants.CELL_JOB_STATUS_END_LIST);
		condition.addFilter("activeFlag", true);
		condition.addOrder("cellCd", true);
		List<WorkCell> cellList = this.queryManager.selectList(WorkCell.class, condition);

		// 2. 박스 팩 리스트 생성
		List<BoxPack> boxPacks = new ArrayList<BoxPack>();
		
		// 3. 작업 셀 정보가 없으면 리턴
		if(ValueUtil.isEmpty(cellList)) {
			return boxPacks;
		}
		
		// 4. 배치 내 상태가 '피킹 시작' or '피킹 완료' 인 작업 데이터를 셀 별로 모두 조회
		condition.removeFilter("activeFlag");
		condition.setFilter("status", SysConstants.IN, LogisConstants.JOB_STATUS_PF);
		condition.addFilter("pickedQty", ">=", 0);
		
		for(WorkCell cell : cellList) {
			condition.setFilter("subEquipCd", cell.getCellCd());
			List<JobInstance> jobList = this.queryManager.selectList(JobInstance.class, condition);
			
			if(ValueUtil.isNotEmpty(jobList)) {
				boxPacks.add(this.fullBoxing(batch, cell, jobList));
			}
		}
		
		// 5. 결과 리턴 
		return boxPacks;
	}

	@Override
	public BoxPack cancelFullboxing(BoxPack box) {
		UndoBoxingEvent event = new UndoBoxingEvent(SysEvent.EVENT_STEP_ALONE, box);
		this.eventPublisher.publishEvent(event);
		return box;
	}

	/**
	 * BoxPack 생성
	 * 
	 * @param batch
	 * @param jobList
	 * @param cell
	 * @return
	 */
	private BoxPack createNewBoxPack(JobBatch batch, List<JobInstance> jobList, WorkCell cell) {
		BoxPack boxPack = ValueUtil.populate(batch, new BoxPack());
		ValueUtil.populate(jobList.get(0), boxPack);
		boxPack.setId(null);
		boxPack.setStatus(BoxPack.BOX_STATUS_BOXED);
		this.queryManager.insert(boxPack);
		return boxPack;
	}

	/**
	 * 작업 정보 기준으로 BoxItem 생성
	 *
	 * @param boxPack
	 * @param jobList
	 */
	private void generateBoxItemsBy(BoxPack boxPack, List<JobInstance> jobList) {
		// 1. 주문 정보 조회
		List<Order> sources = this.searchOrdersForBoxItems(jobList.get(0));
 		
 		// 2. 박스 내품 내역 
 		List<BoxItem> boxItems = new ArrayList<BoxItem>(sources.size());
 		 		
 		// 3. 주문에 피킹 확정 수량 업데이트
		for(Order source : sources) {
			BoxItem boxItem = new BoxItem();
			boxItem = ValueUtil.populate(source, boxItem);
			boxItem.setId(null);
			boxItem.setPickQty(source.getPickedQty());
			boxItem.setPassFlag(false);
			boxItem.setStatus(BoxPack.BOX_STATUS_BOXED);
		};
		
		// 4. 박스 내품 내역 생성
		AnyOrmUtil.insertBatch(boxItems, 100);
	}
	
	/**
	 * 박스 내품 내역을 생성하기 위해 주문 내역 정보를 조회
	 * 
	 * @param job
	 * @return
	 */
	private List<Order> searchOrdersForBoxItems(JobInstance job) {
		StringJoiner sql = new StringJoiner(SysConstants.LINE_SEPARATOR);
		sql.add("SELECT")
		   .add("	ID, DOMAIN_ID, '" + job.getBoxPackId() + "' AS BOX_PACK_ID, ORDER_NO, ORDER_LINE_NO, ORDER_DETAIL_ID, COM_CD, CLASS_CD, SHOP_CD, SKU_CD, SKU_NM, PACK_TYPE, ORDER_QTY, PICKED_QTY, BOXED_QTY")
		   .add("FROM")
		   .add("	ORDERS")
		   .add("WHERE")
		   .add("	DOMAIN_ID = :domainId AND BATCH_ID = :batchId AND COM_CD = :comCd AND CLASS_CD = :classCd AND PICKED_QTY > 0 AND PICKED_QTY > BOXED_QTY)")
		   .add("ORDER BY")
		   .add("	ORDER_NO ASC, ORDER_LINE_NO ASC, SKU_CD ASC, ORDER_QTY DESC");

		Map<String, Object> params = 
				ValueUtil.newMap("domainId,batchId,comCd,classCd", job.getDomainId(), job.getBatchId(), job.getComCd(), job.getClassCd());
		return this.queryManager.selectListBySql(sql.toString(), params, Order.class, 0, 0);
	}

}
