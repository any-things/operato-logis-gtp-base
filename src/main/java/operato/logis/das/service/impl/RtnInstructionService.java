package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.RtnQueryStore;
import operato.logis.das.service.util.RtnBatchJobConfigUtil;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.service.api.IInstructionService;
import xyz.anythings.base.service.impl.LogisServiceDispatcher;
import xyz.anythings.base.util.LogisBaseUtil;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil; 

/**
 * 반품용 작업지시 서비스
 * 
 * @author shortstop
 */
@Component("rtnInstructionService")
public class RtnInstructionService  extends AbstractQueryService  implements IInstructionService {

	/**
	 * 반품 작업 쿼리 스토어
	 */
	@Autowired
	private RtnQueryStore rtnQueryStore;
	
	@Override
	public Map<String, Object> searchInstructionData(JobBatch batch, Object... params) {
		String sql = this.rtnQueryStore.getRtnInstructionSummaryDataQuery();
		Long domainId = batch.getDomainId();
		Map<String,Object> param = ValueUtil.newMap("domainId,batchId", domainId, batch.getId());
		Map<?, ?> cntResult = this.queryManager.selectBySql(sql, param, Map.class);
 
		Query condition = AnyOrmUtil.newConditionForExecution(domainId, 0, 0);
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("equipCd", "is_not_null", LogisConstants.EMPTY_STRING);
		condition.addFilter("subEquipCd", "is_not_null", LogisConstants.EMPTY_STRING);
		condition.addOrder("cellAssgnCd", true);
		List<OrderPreprocess> preprocesses = this.queryManager.selectList(OrderPreprocess.class, condition);

		return ValueUtil.newMap("list,totalOrderCnt,totalSkuCnt,totalPcs", preprocesses, cntResult.get("order_cnt"), cntResult.get("sku_cnt"), cntResult.get("total_pcs"));
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
		// 반품에서는 사용 안 함
		return 0;
	}

	@Override
	public int mergeBatch(JobBatch mainBatch, JobBatch newBatch, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int cancelInstructionBatch(JobBatch batch) {
		int cancelCount = 0;
		
		if(this.beforeCancelInstructionBatch(batch)) {
			cancelCount += this.doCancelInstructionBatch(batch);
			this.afterCancelInstructionBatch(batch);
		}
		
		return cancelCount;
	}
	
	/**
	 * 작업 취소 전 처리 액션
	 * 
	 * @param batch
	 * @return
	 */
	protected boolean beforeCancelInstructionBatch(JobBatch batch) {
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter(new Filter("pickedQty", OrmConstants.GREATER_THAN, 0));
		
		if(this.queryManager.selectSize(JobInstance.class, condition) > 0) {
			throw new ElidomRuntimeException("분류 작업시작 이후여서 취소가 불가능합니다");
		}
		
		return true;
	}
	
	/**
	 * 작업 취소 처리 로직
	 * 
	 * @param batch
	 * @return
	 */
	protected int doCancelInstructionBatch(JobBatch batch) {
		// 1. 작업 배치 정보 조회
		Long domainId = batch.getDomainId();
		String rackCd = batch.getEquipCd();
		
		// 2. 배치 호기 코드가 존재하면
		Rack rack = AnyEntityUtil.findEntityBy(domainId, true, Rack.class, null, "domainId,rackCd", domainId, rackCd);
		rack.setBatchId(null);
		this.queryManager.update(rack, "batchId", "updaterId", "updatedAt");
		
		// 3. 작업 배치 정보 업데이트 
		batch.setStatus(JobBatch.STATUS_READY);
		batch.setInstructedAt(null);
		batch.setBatchOrderQty(0);
		batch.setBatchPcs(0);
		this.queryManager.update(batch, "status", "instructedAt", "batchOrderQty", "batchPcs");
		
		// 4. WorkCell 정보 삭제
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId", domainId, batch.getId());
		String sql = "delete work_cells where domain_id = :domainId and batch_id = :batchId";
		int count = this.queryManager.executeBySql(sql, params);
				
		// 5. 작업 실행 데이터 삭제
		this.queryManager.deleteList(JobInstance.class, params);
		
		return count;
	}	
	
	/**
	 * 작업 취소 후 처리 액션
	 * 
	 * @param batch
	 * @return
	 */
	protected void afterCancelInstructionBatch(JobBatch batch) {
		// 작업 지시 시점에 표시기 점등했다면 표시기 소등
		if(RtnBatchJobConfigUtil.isIndOnAssignedCellWhenInstruction(batch)) {
			BeanUtil.get(LogisServiceDispatcher.class).getIndicationService(batch).indicatorOffAll(batch);
		}
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
		// 1. 배치 내 할당 안 된 주문 가공 정보, 주문 정보를 새로운 배치로 잘라낸다.
		List<OrderPreprocess> preprocesses = this.cutoffNotAssignedOrders(batch);
				
		// 2. 주문 가공 정보로 부터 호기 리스트 조회
		Long domainId = batch.getDomainId();
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addSelect("id", "rackCd", "rackNm", "status", "batchId");
		condition.addFilter("rackCd", "in", AnyValueUtil.filterValueListBy(preprocesses, "equipCd"));
		condition.addOrder("rackCd", false);
		List<Rack> rackList = this.queryManager.selectList(Rack.class, condition);

		// 3. 랙 중에 현재 작업 중인 랙이 있는지 체크 
		for(Rack rack : rackList) {
			if(ValueUtil.isNotEmpty(rack.getBatchId())) {
				// 랙에 다른 작업 배치가 할당되어 있습니다
				throw ThrowUtil.newValidationErrorWithNoLog(true, "ASSIGNED_ANOTHER_BATCH_AT_RACK", ValueUtil.toList(rack.getRackNm()));
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
	 * 배치 내 할당되지 않은 주문을 컷오프 시킨다.
	 * 
	 * @param batch
	 * @return
	 */
	private List<OrderPreprocess> cutoffNotAssignedOrders(JobBatch batch) {
		// 1. 새로운 배치 ID 생성
		String cutoffBatchId = LogisBaseUtil.newJobBatchId(batch.getDomainId());
		
		// 2. 랙이 할당되지 않은 주문 가공 리스트를 새로운 배치로 업데이트
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,newBatchId", batch.getDomainId(), batch.getId(), cutoffBatchId);
		String sql = "UPDATE ORDER_PREPROCESSES SET BATCH_ID = :newBatchId WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId AND (EQUIP_CD IS NULL OR SUB_EQUIP_CD IS NULL)";
		int updatedCnt = this.queryManager.executeBySql(sql, params);
		
		if(updatedCnt > 0) {
			// 3. 존재한다면 새로운 배치 번호를 얻어서 할당 안 된 주문 가공 및 주문 리스트의 배치 번호 업데이트
			sql = "UPDATE ORDERS SET BATCH_ID = :newBatchId WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId AND CLASS_CD IN (SELECT CELL_ASSGN_CD FROM ORDER_PREPROCESSES WHERE DOMAIN_ID = :domainId AND BATCH_ID = :newBatchId)";
			this.queryManager.executeBySql(sql, params);
			
			// 4. 새로운 배치 생성
			JobBatch newBatch = AnyValueUtil.populate(batch, new JobBatch());
			newBatch.setId(cutoffBatchId);
			newBatch.setEquipCd(null);
			newBatch.setEquipNm(null);
			newBatch.setStatus(JobBatch.STATUS_WAIT);
			this.queryManager.insert(newBatch);
		}
		
		// 5. 이전 배치 주문 가공 정보 조회 후 리턴
		Query query = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		query.addFilter("batchId", batch.getId());
		return this.queryManager.selectList(OrderPreprocess.class, query);
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
		this.updateOrdersBy(batch, equipPreprocesses);
		
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
	private void updateOrdersBy(JobBatch batch, List<OrderPreprocess> sources) {
		Long domainId = batch.getDomainId();
		String mainBatchId = batch.getBatchGroupId();
		String newBatchId = batch.getId();

		OrderPreprocess first = sources.get(0);
		Map<String, Object> params = ValueUtil.newMap("domainId,mainBatchId,newBatchId,status,equipType,equipCd,equipNm,subEquipCd,currentDate", domainId, mainBatchId, newBatchId, Order.STATUS_WAIT, first.getEquipType(), first.getEquipCd(), first.getEquipNm(), first.getSubEquipCd(), new Date());
		String sql = "update orders set batch_id = :newBatchId, status = :status, equip_type = :equipType, equip_cd = :equipCd, equip_nm = :equipNm, sub_equip_cd = :subEquipCd, updated_at = :currentDate where domain_id = :domainId and batch_id = :mainBatchId and com_cd = :comCd and class_cd = :classCd";
		
		for(OrderPreprocess source : sources) {
			params.put("subEquipCd", source.getSubEquipCd());
			params.put("comCd", source.getComCd());
			params.put("classCd", source.getCellAssgnCd());
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
		String insertQuery = this.rtnQueryStore.getRtnGenerateJobsByInstructionQuery();
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
			c.setClassCd(source.getCellAssgnCd());
			c.setActiveFlag(true);
			cellList.add(c);
		}
		
		AnyOrmUtil.insertBatch(cellList, 100);
	}
 
}
