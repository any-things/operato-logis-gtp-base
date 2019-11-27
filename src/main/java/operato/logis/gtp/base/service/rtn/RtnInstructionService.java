package operato.logis.gtp.base.service.rtn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component; 

import operato.logis.gtp.base.query.store.GtpQueryStore;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.service.api.IInstructionService;
import xyz.anythings.base.util.LogisBaseUtil;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil; 

/**
 * 반품용 작업지시 서비스
 * 
 * @author shortstop
 */
@Component("rtnInstructionService")
public class RtnInstructionService  extends AbstractQueryService  implements IInstructionService{

	@Autowired
	private GtpQueryStore QueryStore;
	
	@Override
	public Map<String, Object> searchInstructionData(JobBatch batch, Object... params) {
		String sql = QueryStore.getRtnInstructionSummaryDataQuery();
		Long domainId = batch.getDomainId();
		Map<String,Object> param = ValueUtil.newMap("domainId,batchId", domainId, batch.getId());
		Map<?, ?> cntResult = this.queryManager.selectBySql(sql, param, Map.class);
 
		Query condition = AnyOrmUtil.newConditionForExecution(domainId, 0, 0);
		condition.addFilter("batchId", batch.getId());
		condition.addOrder("cellAssgnCd", true);
		List<OrderPreprocess> preprocesses = this.queryManager.selectList(OrderPreprocess.class, condition);

		return ValueUtil.newMap("list,skuCnt,pcsCnt,custCnt", preprocesses, cntResult.get("sku_cnt"), cntResult.get("pcs_cnt"), cntResult.get("cust_cnt"));
	}
	
	@Override
	public int instructBatch(JobBatch batch, List<String> equipCdList, Object... params) {
		int instructCount = 0;

		if(this.beforeInstructBatch(batch, equipCdList)) {
			instructCount += this.doInstructBatch(batch, equipCdList);
		}

		return instructCount;
	}

	@Override
	public int instructTotalpicking(JobBatch batch, List<String> equipIdList, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int mergeBatch(JobBatch mainBatch, JobBatch newBatch, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int cancelInstructionBatch(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	/**
	 * 작업 지시 전 처리 액션
	 *
	 * @param batch
	 * @param rackList
	 * @return
	 */
	protected boolean beforeInstructBatch(JobBatch batch, List<String> equipIdList) {
		// 배치 상태가 작업 지시 상태인지 체크
		if(ValueUtil.isNotEqual(batch.getStatus(), JobBatch.STATUS_READY)) {
			// '작업 지시 대기' 상태가 아닙니다
			throw ThrowUtil.newValidationErrorWithNoLog(MessageUtil.getTerm("terms.text.is_not_wait_state", "JobBatch status is not 'READY'"));
		}

		return true;
	}
	
	/**
	 * 작업 지시 처리 로직
	 *
	 * @param batch
	 * @param regionList
	 * @return
	 */
	protected int doInstructBatch(JobBatch batch, List<String> regionList) {
		// 1. 배치의 주문 가공 정보 조회
		Long domainId = batch.getDomainId();
		Query query = AnyOrmUtil.newConditionForExecution(domainId);
		query.addFilter("batchId", batch.getId());  
		List<OrderPreprocess> preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		
		// 2. 주문 가공 정보로 부터 호기 리스트 조회
		List<String> equipCdList = AnyValueUtil.filterValueListBy(preprocesses, "equipCd");
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addSelect("id", "rackCd", "rackNm", "status", "batchId");
		condition.addFilter("rackCd", "in", equipCdList);
		condition.addOrder("rackCd", false);
		List<Rack> rackList = this.queryManager.selectList(Rack.class, condition);

		// 3. 랙 중에 현재 작업 중인 랙이 있는지 체크 
		for(Rack rack : rackList) {
			if(ValueUtil.isEqualIgnoreCase(rack.getStatus(), "RUN")) {
				// 호기에 다른 작업 배치가 할당되어 있습니다
				throw ThrowUtil.newValidationErrorWithNoLog(true, "ASSIGNED_ANOTHER_BATCH", ValueUtil.toList(batch.getEquipNm()));
			}
		}
		
		// 4. 랙 리스트를 돌면서 배치 생성, 주문 가공 정보 업데이트, 주문 정보 업데이트, 작업 정보 생성, WorkCell 정보 생성, 호기 정보 업데이트 처리
		int rackCount = rackList.size();
		for(int i = 0 ; i < rackCount ; i++) {
			// 4-1. 랙 추출
			Rack rack = rackList.get(i);
			// 4-2. 랙 별 주문 가공 데이터 추출
			List<OrderPreprocess> equipPreprocesses = AnyValueUtil.filterListBy(preprocesses, "equipCd", rack.getRackCd());
			// 4-3. 마지막 번째 설비는 메인 배치, 그 외 설비는 새로운 배치를 생성하여 
			String batchId = (i != rackCount - 1) ? LogisBaseUtil.newJobBatchId(domainId) : batch.getId();
			JobBatch newBatch = this.sliceBatch(batch, batchId, rack, equipPreprocesses);
			// 4-4. 각 배치 별로 작업지시 처리
			this.instructBySlicedBatch(newBatch, rack, equipPreprocesses);
		}
		
		// 5. 주문 가공 정보 배치 ID 업데이트
		AnyOrmUtil.updateBatch(preprocesses, 100, "batchId");
		// 6. 랙 정보 배치 ID 업데이트
		AnyOrmUtil.updateBatch(rackList, 100, "status", "batchId");
		// 7. 결과 건수 리턴
		return preprocesses.size();
	}
	
	/**
	 * 랙 별 분할된 배치별로 작업 지시를 수행한다. 
	 * 
	 * @param batch
	 * @param rack
	 * @param equipPreprocesses
	 */
	private void instructBySlicedBatch(JobBatch batch, Rack rack, List<OrderPreprocess> equipPreprocesses) {
		// 1. 주문 정보 업데이트 - 배치 ID, 랙 코드, 셀 코드
		this.updateOrderBy(batch, equipPreprocesses);
		
		// 2. 작업 정보 생성
		this.generateJobInstancesBy(batch);
		
		// 3. WorkCell 정보 생성
		this.generateWorkCellBy(batch, equipPreprocesses);
		
		// 4. Rack 업데이트
		rack.setStatus(JobBatch.STATUS_RUNNING);
		rack.setBatchId(batch.getId());
	}
	
	/**
	 * 메인 배치로 부터 랙 별 배치를 별도 생성한다. 
	 *  
	 * @param mainBatch
	 * @param newBatchId
	 * @param rack
	 * @param equipPreprocesses
	 * @return
	 */
	private JobBatch sliceBatch(JobBatch mainBatch, String newBatchId, Rack rack, List<OrderPreprocess> equipPreprocesses) {
		for(OrderPreprocess op : equipPreprocesses) {
			op.setBatchId(newBatchId);
		}
		
		int batchPcs = equipPreprocesses.stream().mapToInt(item -> item.getTotalPcs()).sum();
		
		if(ValueUtil.isEqualIgnoreCase(mainBatch.getId(), newBatchId)) {
			mainBatch.setStatus(JobBatch.STATUS_RUNNING);
			mainBatch.setEquipType(LogisConstants.EQUIP_TYPE_RACK);
			mainBatch.setEquipCd(rack.getRackCd());
			mainBatch.setEquipNm(rack.getRackNm());
			mainBatch.setInstructedAt(new Date());
			mainBatch.setBatchOrderQty(equipPreprocesses.size());
			mainBatch.setBatchPcs(batchPcs);
			this.queryManager.update(mainBatch, "status", "equipType", "equipCd", "equipNm", "instructedAt", "batchOrderQty", "batchPcs", "updatedAt");
			return mainBatch;
			
		} else {
			JobBatch newBatch = AnyValueUtil.populate(mainBatch, new JobBatch());
			newBatch.setId(newBatchId);
			newBatch.setEquipCd(rack.getRackCd());
			newBatch.setEquipNm(rack.getRackNm());
			newBatch.setBatchOrderQty(equipPreprocesses.size());
			newBatch.setBatchPcs(batchPcs);
			newBatch.setStatus(JobBatch.STATUS_RUNNING);
			newBatch.setInstructedAt(new Date());
			this.queryManager.insert(newBatch);
			return newBatch;
		}
	}
	
	/**
	 * 주문 정보를 주문 가공 정보를 토대로 업데이트
	 *
	 * @param batch
	 * @param sources
	 */
	private void updateOrderBy(JobBatch batch, List<OrderPreprocess> sources) {
		Long domainId = batch.getDomainId();
		String mainBatchId = batch.getBatchGroupId();
		String newBatchId = batch.getId();

		OrderPreprocess first = sources.get(0);
		Map<String, Object> params = ValueUtil.newMap("domainId,mainBatchId,newBatchId,status,equipType,equipCd,equipNm,subEquipCd,currentDate", domainId, mainBatchId, newBatchId, Order.STATUS_WAIT, first.getEquipType(), first.getEquipCd(), first.getEquipNm(), first.getSubEquipCd(), new Date());
		String sql = "update orders set batch_id = :newBatchId, status = :status, equip_type = :equipType, equip_cd = :equipCd, equip_nm = :equipNm, sub_equip_cd = :subEquipCd, updated_at = :currentDate where domain_id = :domainId and batch_id = :mainBatchId and com_cd = :comCd and sku_cd = :skuCd";
		
		for(OrderPreprocess source : sources) {
			params.put("subEquipCd", source.getSubEquipCd());
			params.put("comCd", source.getComCd());
			params.put("skuCd", source.getCellAssgnCd());
			this.queryManager.executeBySql(sql, params);
		}
	}
	
	/**
	 * 작업 데이터 생성
	 *
	 * @param batch
	 */
	private void generateJobInstancesBy(JobBatch batch) {
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		String insertQuery = this.QueryStore.getRtnGenerateJobInstancesQuery();
		this.queryManager.executeBySql(insertQuery, params);
	}
	
	/**
	 * Work Cell 생성
	 *
	 * @param batch
	 * @param sources
	 */
	private void generateWorkCellBy(JobBatch batch, List<OrderPreprocess> sources) {
		// 1. 기존 WorkCell 삭제
		Long domainId = batch.getDomainId();
		List<String> subEquipCdList = AnyValueUtil.filterValueListBy(sources, "subEquipCd");
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addFilter("cellCd", "in", subEquipCdList);
		this.queryManager.deleteList(WorkCell.class, condition);
		
		// 2. 새로 생성
		List<WorkCell> cellList = new ArrayList<WorkCell>(sources.size());
		for(OrderPreprocess source : sources) {
			WorkCell c = new WorkCell();
			c.setDomainId(domainId);
			c.setBatchId(batch.getId());
			c.setCellCd(source.getSubEquipCd());
			c.setComCd(source.getComCd());
			c.setJobType(batch.getJobType());
			c.setLastPickedQty(0);
			c.setSkuCd(source.getCellAssgnCd());
			c.setActiveFlag(true);
			cellList.add(c);
		}
		
		AnyOrmUtil.insertBatch(cellList, 100);
	}
 
}
