package operato.logis.das.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.DasQueryStore;
import operato.logis.das.service.model.OrderGroup;
import operato.logis.das.service.model.PreprocessStatus;
import operato.logis.das.service.model.PreprocessSummary;
import operato.logis.das.service.model.RackCells;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.event.EventConstants;
import xyz.anythings.base.event.main.BatchPreprocessEvent;
import xyz.anythings.base.service.api.IPreprocessService;
import xyz.anythings.base.service.util.StageJobConfigUtil;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.sys.util.ValueUtil;
import xyz.elidom.util.BeanUtil;

/**
 * 출고 주문 가공 서비스
 * 
 * @author shortstop
 */
@Component("dasPreprocessService")
public class DasPreprocessService extends AbstractExecutionService implements IPreprocessService {
	/**
	 * 쿼리 스토어
	 */
	@Autowired
	private DasQueryStore dasQueryStore;
	
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, ?> buildPreprocessSet(JobBatch batch, Query query) {
		// 1. 주문 가공 요약 후 처리 이벤트 전송
		BatchPreprocessEvent event = new BatchPreprocessEvent(batch, SysEvent.EVENT_STEP_ALONE, EventConstants.EVENT_PREPROCESS_SUMMARY);
		event = (BatchPreprocessEvent)this.eventPublisher.publishEvent(event);
		
		// 2. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			Object result = event.getEventResultSet() != null && event.getEventResultSet().getResult() != null ? event.getEventResultSet().getResult() : null;
			if(result instanceof Map) {
				return (Map<String, ?>)result;
			}
		}
		
		// 3. 주문 가공 정보 조회  
		List<OrderPreprocess> preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		
		// 4. 주문 가공 정보가 존재하지 않는다면 주문 정보로 생성
		if(ValueUtil.isEmpty(preprocesses)) {
			this.generatePreprocess(batch);
			preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		}
		
		// 5. 주문 그룹 정보 - 주문 가공 화면의 중앙 주문 그룹 리스트
		List<OrderGroup> groups = this.searchOrderGroupList(batch);
		// 6. 호기 할당 셀 요약 정보 - 주문 가공 화면의 우측 호기별 할당 셀 리스트
		List<RackCells> rackCells = this.rackAssignmentStatus(batch);
		// 7. 물량 요약 정보 - 주문 가공 화면의 상단 물량 요약 정보
		PreprocessSummary summary = this.searchPreprocessSummary(batch);
		// 8. 리턴 데이터 셋
		return ValueUtil.newMap("racks,groups,preprocesses,summary", rackCells, groups, preprocesses, summary);
	}

	@Override
	public int deletePreprocess(JobBatch batch) {
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		return this.queryManager.deleteList(OrderPreprocess.class, condition);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<JobBatch> completePreprocess(JobBatch batch, Object... params) {
		// 1. 주문 가공 처리 이벤트 전송
		BatchPreprocessEvent event = new BatchPreprocessEvent(batch, SysEvent.EVENT_STEP_ALONE, EventConstants.EVENT_PREPROCESS_COMPLETE);
		event = (BatchPreprocessEvent)this.eventPublisher.publishEvent(event);
		
		// 2. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			Object result = event.getEventResultSet() != null && event.getEventResultSet().getResult() != null ? event.getEventResultSet().getResult() : null;
			if(result instanceof List<?>) {
				return (List<JobBatch>)result;
			}
		}
		
		// 3. 주문 가공 정보가 존재하는지 체크
		this.beforeCompletePreprocess(batch, false);

		// 4. 주문 가공 완료 처리
		this.completePreprocessing(batch);

		// 5. 주문 가공 완료 처리한 배치 리스트 리턴
		return ValueUtil.toList(batch);

	}

	@Override
	public void resetPreprocess(JobBatch batch, boolean resetAll, List<String> equipCdList) {
		// 1. 배치 리셋을 위한 배치 정보 체크
		this.checkJobBatchesForReset(batch);
		
		// 2. 할당 정보 리셋 
		if(resetAll) {
			this.generatePreprocess(batch);
		} else {
			String qry = this.dasQueryStore.getDasResetRackCellQuery();
			this.queryManager.executeBySql(qry, ValueUtil.newMap("domainId,batchId,equipCds", batch.getDomainId(), batch.getId(), equipCdList));	
		}
		
		// 3. 작업 배치 상태 - 주문가공대기 상태로 업데이트
		batch.setStatus(JobBatch.STATUS_WAIT);
		this.queryManager.update(batch, "status");
	}

	@Override
	public int assignEquipLevel(JobBatch batch, String equipCds, List<OrderPreprocess> items, boolean automatically) {
 		// 1. 상품 정보가 존재하는지 체크
		if(ValueUtil.isEmpty(items)) {
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_AVAILABLE_OJBECT", "terms.label.order");
		}
		
		// 2. 자동 랙 지정
		if(automatically) {
			assignRackByAuto(batch, equipCds, items);
		// 3. 수동 랙 지정
		} else {
			assignRackByManual(batch, equipCds, items);
		}
		
		return items.size(); 
	}
	
	@Override
	public int assignSubEquipLevel(JobBatch batch, String equipType, String equipCd, List<OrderPreprocess> items) {
		// 1. 셀 할당 처리 이벤트 전송
		BatchPreprocessEvent event = new BatchPreprocessEvent(batch, SysEvent.EVENT_STEP_ALONE, EventConstants.EVENT_PREPROCESS_SUB_EQUIP_ASSIGN);
		event = (BatchPreprocessEvent)this.eventPublisher.publishEvent(event);
		
		// 2. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			Object result = event.getEventResultSet() != null && event.getEventResultSet().getResult() != null ? event.getEventResultSet().getResult() : null;
			return (result instanceof Integer) ? ValueUtil.toInteger(result) : 0;
		}
		
		// 3. 랙 리스트 조회
		List<String> equipList = AnyValueUtil.filterValueListBy(items, "equipCd");
		List<RackCells> rackCells = this.rackAssignmentStatus(batch, equipList);
		
		// 4. 설비 서브 코드 (셀, 슈트 등) 할당 
		this.assignCells(batch, rackCells, items);
		return items.size();
	}
	
	/**
	 * 주문 가공 정보에 호기 지정
	 *
	 * @param batch		
	 * @param equipCd
	 * @param items
	 */
	public void assignRackByManual(JobBatch batch, String equipCd, List<OrderPreprocess> items) {
		// 1. 수동 랙 지정 이벤트 전송
		BatchPreprocessEvent event = new BatchPreprocessEvent(batch, SysEvent.EVENT_STEP_ALONE, EventConstants.EVENT_PREPROCESS_EQUIP_MANUAL_ASSIGN);
		event = (BatchPreprocessEvent)this.eventPublisher.publishEvent(event);
		
		// 2. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			return;
		}
		
		// 3. 이미 랙이 지정 되어 있는 상품 개수 조회
		Rack rack = Rack.findByRackCd(batch.getDomainId(), equipCd, false);
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,equipCd", batch.getDomainId(), batch.getId(), equipCd);
		int assignedCount = this.queryManager.selectSize(OrderPreprocess.class, params);

		// 4. 새로 랙에 할당할 상품 개수 합이 랙의 셀 보다 많을 때 예외 발생
		int cellCount = rack.validLocationCount();
		if(cellCount < assignedCount + items.size()) {
			// 랙의 빈 셀 개수보다 할당할 주문 수가 많습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "MISMATCH_ORDER_AND_EMPTY_CELL");
		}
		
		// 5. 랙 지정
		for(OrderPreprocess preprocess : items) {
			preprocess.setEquipCd(rack.getRackCd());
			preprocess.setEquipNm(rack.getRackNm());
		}
		
		// 6. 주문 가공 정보 업데이트 
		AnyOrmUtil.updateBatch(items, 100, "equipCd", "equipNm", "updatedAt");
	}

	/**
	 * 주문 가공 정보에 호기 자동 할당 
	 *
	 * @param batch
	 * @param equipCds
	 * @param items
	 * @return
	 */
	public int assignRackByAuto(JobBatch batch, String equipCds, List<OrderPreprocess> items) {
		// 1. 자동 랙 지정 처리 이벤트 전송
		BatchPreprocessEvent event = new BatchPreprocessEvent(batch, SysEvent.EVENT_STEP_ALONE, EventConstants.EVENT_PREPROCESS_EQUIP_AUTO_ASSIGN);
		event = (BatchPreprocessEvent)this.eventPublisher.publishEvent(event);
		
		// 2. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			Object result = event.getEventResultSet() != null && event.getEventResultSet().getResult() != null ? event.getEventResultSet().getResult() : null;
			return (result instanceof Integer) ? ValueUtil.toInteger(result) : 0;
		}
		
		// 3. 주문 가공 정보 호기 매핑 리셋
		String qry = this.dasQueryStore.getDasResetRackCellQuery();
		this.queryManager.executeBySql(qry, ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId()));
		
		// 4. 호기 리스트 조회
		int skuCount = items.size();
		List<RackCells> rackCells = this.rackAssignmentStatus(batch);
		
		// 5. 상품 개수와 호기의 사용 가능한 셀 개수를 비교해서
		int rackCapa = 0;
		for(RackCells rackCell : rackCells) {
			rackCapa += rackCell.getRemainCells();
		}

		// 6. 거래처 개수가 호기의 사용 가능 셀 개수보다 크다면 큰 만큼 거래처 리스트에서 삭제
		int removalCount = skuCount - rackCapa;
		if(removalCount > 0) {
			// 거래처 리스트를 호기의 사용 가능 셀 만큼만 남기고 나머지는 제거
			for(int i = 0 ; i < removalCount ; i++) {
				items.remove(items.size() - 1);
			}
		}

		boolean idxGoForward = true;
		int rackIdx = 0;
		int rackEndIdx = rackCells.size();
				
		// 7. 주문 가공별로 루프
		for(OrderPreprocess preprocess : items) {
			if(preprocess.getId().isEmpty()) {
				break;
			}
			
			preprocess.setEquipCd(SysConstants.EMPTY_STRING);
			preprocess.setEquipNm(SysConstants.EMPTY_STRING);

			if(rackIdx == rackEndIdx) {
				rackIdx = rackEndIdx - 1;
				idxGoForward = false;

			} else if(rackIdx == -1) {
				rackIdx = 0;
				idxGoForward = true;
			}

			// 번갈아 가면서 호기를 찾아서 상품과 매핑
			RackCells rackCell = rackCells.get(rackIdx);
			preprocess.setEquipCd(rackCell.getRackCd());
			preprocess.setEquipNm(rackCell.getRackNm());
			rackCell.setAssignedCells(rackCell.getAssignedCells() + 1);
			rackCell.setRemainCells(rackCell.getRemainCells() - 1);

			// 상품에 남은 셀 개수가 없다면 호기 리스트에서 제거
			if(rackCell.getRemainCells() == 0) {
				rackCells.remove(rackCell);
				rackEndIdx--;
			} else {
				rackIdx = idxGoForward ? rackIdx + 1 : rackIdx - 1;
			} 
			 
		}
		  
		// 8. 주문 가공 정보 업데이트
		AnyOrmUtil.updateBatch(items, 100, "equipCd", "equipNm", "updatedAt");
		return items.size();
	}
	
	/**
	 * 작업 배치 별 주문 그룹 리스트
	 *
	 * @param batch
	 * @return
	 */
	public List<OrderGroup> searchOrderGroupList(JobBatch batch) {
		// 스테이지 분류 설정에서 주문 그룹과 매핑할 필드명 조회
		// String orderGroupFieldName = StageJobConfigUtil.getOrderGroupField(batch.getStageCd(), batch.getJobType());
		// 주문 그룹 리스트 쿼리 조회
		String sql = this.dasQueryStore.getOrderGroupListQuery();
		// 주문 그룹 리스트 쿼리에서 CLASS_CD를 주문 그룹과 매핑할 필드명으로 Replace
		// sql = sql.replaceAll("CLASS_CD", orderGroupFieldName);
		// 쿼리 실행
		return this.queryManager.selectListBySql(sql, ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId()), OrderGroup.class, 0, 0);
	}
	
	/**
	 * 작업 배치 별 호기별 물량 할당 요약 정보를 조회하여 리턴
	 *
	 * @param batch 
	 * @return
	 */
	public PreprocessSummary searchPreprocessSummary(JobBatch batch) {
		String sql = this.dasQueryStore.getDasPreprocessSummaryQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,stageCd,jobType", batch.getDomainId(), batch.getId(), batch.getStageCd(), batch.getJobType());
		return this.queryManager.selectBySql(sql, params,PreprocessSummary.class);
	}
	
	/**
	 * 배치 리셋을 위한 배치 정보 체크
	 *
	 * @param batch
	 * @return
	 */
	private JobBatch checkJobBatchesForReset(JobBatch batch) {
		String batchStatus = batch.getStatus();
		
		if(!ValueUtil.isEqualIgnoreCase(batchStatus, JobBatch.STATUS_WAIT) &&
		   !ValueUtil.isEqualIgnoreCase(batchStatus, JobBatch.STATUS_READY)) {
			 // 주문 가공 대기, 작업 지시 대기 상태에서만 할당정보 리셋이 가능합니다.
	 		 throw ThrowUtil.newValidationErrorWithNoLog(true, "ONLY_ALLOWED_RESET_ASSIGN_ONLY");
		}

		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter(new Filter("pickedQty", OrmConstants.GREATER_THAN, 0));

		// 분류 작업 시작한 개수가 있는지 체크
		if(this.queryManager.selectSize(Order.class, condition) > 0) {
			// 분류 작업시작 이후여서 할당정보 리셋 불가능합니다.
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NOT_ALLOWED_RESET_ASSIGN_AFTER_START_JOB");
		}

		return batch;
	}
	
	/**
	 * 작업 배치 별 주문 가공 정보에서 호기별로 상품 할당 상태를 조회하여 리턴
	 *
	 * @param batch
	 * @return
	 */
	public List<RackCells> rackAssignmentStatus(JobBatch batch) {
		String sql = this.dasQueryStore.getDasRackCellStatusQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag", batch.getDomainId(), batch.getId(), batch.getJobType(), batch.getStageCd(), true);
		return this.queryManager.selectListBySql(sql, params, RackCells.class, 0, 0); 
	}
	
	/**
	 * 작업 배치 별 주문 가공 정보에서 호기별로 SKU 할당 상태를 조회하여 리턴
	 *
	 * @param batch
	 * @param equipCds
	 * @return
	 */ 
	public List<RackCells> rackAssignmentStatus(JobBatch batch, List<String> equipCds) {
		String sql = this.dasQueryStore.getDasRackCellStatusQuery(); 
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag,equipCds", batch.getDomainId(), batch.getId(), batch.getJobType(), batch.getStageCd(), true, equipCds);
		return this.queryManager.selectListBySql(sql, params, RackCells.class, 0, 0);
	}

	@Override
	public int generatePreprocess(JobBatch batch, Object... params) {
		// 1. 주문 가공 데이터 삭제  
		this.deletePreprocess(batch);
		
		// 2. 주문 가공 데이터를 생성하기 위해 주문 데이터를 조회
		String cellMappingField = StageJobConfigUtil.getPickingClassCodeField(batch.getStageCd(), batch.getJobType());
		boolean cellSkuMapping = ValueUtil.isEmpty(cellMappingField) ? true : cellMappingField.toLowerCase().startsWith("sku") ? true : false;
		boolean cellShopMapping = ValueUtil.isEmpty(cellMappingField) ? true : cellMappingField.toLowerCase().startsWith("shop") ? true : false;
		boolean cellOrderMapping = ValueUtil.isEmpty(cellMappingField) ? true : cellMappingField.toLowerCase().startsWith("order") ? true : false;
		
		String sql = this.dasQueryStore.getDasGeneratePreprocessQuery();
		Map<String, Object> condition = ValueUtil.newMap("domainId,batchId,cellSkuMapping,cellShopMapping,cellOrderMapping", batch.getDomainId(), batch.getId(), cellSkuMapping, cellShopMapping, cellOrderMapping);
		List<OrderPreprocess> preprocessList = this.queryManager.selectListBySql(sql, condition, OrderPreprocess.class, 0, 0);

		// 3. 주문 가공 데이터를 추가
		int generatedCount = ValueUtil.isNotEmpty(preprocessList) ? preprocessList.size() : 0;
		if(generatedCount > 0) {
			this.queryManager.insertBatch(preprocessList);
		}

		// 4. 결과 리턴
		return generatedCount;
	}
	
	/**
	 * 주문 가공 완료가 가능한 지 체크
	 *
	 * @param batch
	 * @param checkRackAssigned
	 */
	private void beforeCompletePreprocess(JobBatch batch, boolean checkRackAssigned) {
		// 1. 상태 확인
		if(!ValueUtil.isEqualIgnoreCase(batch.getStatus(), JobBatch.STATUS_WAIT) && !ValueUtil.isEqualIgnoreCase(batch.getStatus(), JobBatch.STATUS_READY)) {
			// 상태가 유효하지 않습니다.
			throw ThrowUtil.newValidationErrorWithNoLog(true, "INVALID_STATUS");
		}

		// 2. 주문 가공 정보가 존재하는지 체크
		int preprocessCount = this.preprocessCount(batch, null, null, null);

		if(preprocessCount == 0) {
			throw new ElidomRuntimeException("No preprocess data.");
		}

		// 3. 주문에서 서머리한 정보와 주문 가공 정보 개수가 맞는지 체크
		if(this.checkOrderPreprocessDifferent(batch) > 0) {
			// 수신한 주문 개수와 주문가공 개수가 다릅니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "MISMATCH_RECEIVED_AND_PREPROCESSED");
		}

		// 4. 랙 지정이 안 된 매장이 존재하는지 체크
		if(checkRackAssigned) {
			int notAssignedCount = this.preprocessCount(batch, "equip_cd", "is_blank", OrmConstants.EMPTY_STRING);
			
			if(notAssignedCount > 0) {
				// 랙 지정이 안된 상품이 (notAssignedCount)개 있습니다.
				throw ThrowUtil.newValidationErrorWithNoLog(true, "EXIST_NOT_ASSIGNED_ORDERS", ValueUtil.toList("" + notAssignedCount));
			}
		}
	}
	
	/**
	 * 조건에 따른 주문 가공 데이터 건수를 조회하여 리턴
	 *
	 * @param batch
	 * @param filterNames
	 * @param filterOpers
	 * @param filterValues
	 * @return
	 */
	private int preprocessCount(JobBatch batch, String filterNames, String filterOpers, String filterValues) {
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());

		if(ValueUtil.isNotEmpty(filterNames)) {
			String[] names = filterNames.split(SysConstants.COMMA);
			String[] opers = ValueUtil.isNotEmpty(filterOpers) ? filterOpers.split(SysConstants.COMMA) : SysConstants.EMPTY_STRING.split(SysConstants.COMMA);
			String[] values = ValueUtil.isNotEmpty(filterValues) ? filterValues.split(SysConstants.COMMA) : SysConstants.EMPTY_STRING.split(SysConstants.COMMA);

			for(int i = 0 ; i < names.length ; i++) {
				condition.addFilter(new Filter(names[i], opers[i], values[i]));
			}
		}

		return this.queryManager.selectSize(OrderPreprocess.class, condition);
	}
	
	/**
	 * 배치 정보의 주문 정보와 주문 가공 정보의 개수가 일치하지 않는지 체크
	 *
	 * @param batch
	 * @return
	 */
	private int checkOrderPreprocessDifferent(JobBatch batch) {
		// 1. 주문 테이블 기준으로 작업 배치 테이블에서 상품별로 총 주문 PCS가 다른 상품 리스트를 구한다.
		List<PreprocessStatus> diffByOrder = this.dasOrderPreprocessDiffStatus(batch, "order");
		// 2. 작업 배치 테이블 기준으로 주문 테이블과 상품별로 총 주문 PCS가 다른 상품 리스트를 구한다.
		List<PreprocessStatus> diffByPreprocess = this.dasOrderPreprocessDiffStatus(batch, "preprocess");
		// 3. 두 정보가 하나라도 있으면 일치하지 않는 것이므로 일치하지 않은 개수를 리턴한다.
		return ValueUtil.isEmpty(diffByOrder) && ValueUtil.isEmpty(diffByPreprocess) ? 0 : diffByOrder.size() + diffByPreprocess.size();
	}

	/**
	 * 주문 정보(JobBatch)의 SKU 별 총 주문 개수와 주문 가공 정보(RtnPreprocess)의 SKU 별 총 주문 개수를
	 * SKU 별로 비교하여 같지 않은 거래처의 정보만 조회
	 *
	 * @param diffStandard
	 * @return
	 */
	public List<PreprocessStatus> dasOrderPreprocessDiffStatus(JobBatch batch,String diffStandard) {
		String outerJoinDiretion = ValueUtil.isEqualIgnoreCase(diffStandard, "order") ? "LEFT" : "RIGHT";
		String sql = this.dasQueryStore.getDasOrderPreprocessDiffStatusQuery();
		
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,outerJoinDiretion", batch.getDomainId(), batch.getId(),outerJoinDiretion);
		return BeanUtil.get(IQueryManager.class).selectListBySql(sql, params, PreprocessStatus.class, 0, 0);
	}
	 
	/**
	 * 주문 가공 완료 처리
	 *
	 * @param batch
	 */
	private void completePreprocessing(JobBatch batch) {
		// 1. 주문 가공 정보 조회
		List<OrderPreprocess> items = this.searchPreprocessList(batch);

		if(ValueUtil.isNotEmpty(items)) {
			List<String> equipList = AnyValueUtil.filterValueListBy(items, "equipCd");
			// 2. 랙 리스트 조회
			List<RackCells> rackCells = this.rackAssignmentStatus(batch, equipList);

			if(ValueUtil.isNotEmpty(rackCells)) {
				// 3. 물량이 많은 상품순으로 작업 존 리스트를 왔다갔다 하면서 로케이션 지정을 자동으로 한다.
				this.assignCells(batch, rackCells, items);
				// 4. JobBatch 상태 업데이트
				batch.setStatus(JobBatch.STATUS_READY);
				this.queryManager.update(batch, "status");
			} else {
				// 5. 랙 [subBatch.getEquipCd()]를 사용할 수 없습니다.
				throw ThrowUtil.newValidationErrorWithNoLog(true, "CANNOT_USE_RACK", equipList);
			}
		}
	}

	@Override
	public List<OrderPreprocess> searchPreprocessList(JobBatch batch) {
 		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addSelect("id", "batchId", "comCd", "cellAssgnCd", "cellAssgnNm", "equipCd", "equipNm", "subEquipCd", "skuQty", "totalPcs");
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("subEquipCd", SysConstants.IS_BLANK, SysConstants.EMPTY_STRING);
		condition.addOrder("totalPcs", false);
		return this.queryManager.selectList(OrderPreprocess.class, condition);
	}
	 	 
	/**
	 * 상품별 로케이션 할당
	 *
	 * @param batch
	 * @param rackCells
	 * @param preprocesses
	 */
	private void assignCells(JobBatch batch, List<RackCells> rackCells, List<OrderPreprocess> preprocesses) {
		for(RackCells rackCell : rackCells) {
			// 1. 랙 별로 셀 리스트를 소팅 조건으로 조회
			String rackCd = rackCell.getRackCd();
			List<Cell> cells = this.sortCellBy(batch.getDomainId(), rackCd);
			List<OrderPreprocess> equipPreprocesses = AnyValueUtil.filterListBy(preprocesses, "equipCd", rackCd);
			
			if(equipPreprocesses.size() > cells.size()) {
				// 2. 주문 가공 (상품) 개수가 할당될 셀 개수보다 많으면 에러 - 랙에 상품을 할당할 셀이 존재하지 않습니다
				throw ThrowUtil.newValidationErrorWithNoLog(true, "MISMATCH_ORDER_AND_EMPTY_CELL");
			}
			
			if(ValueUtil.isNotEmpty(cells)) {
				// 3. 주문 가공 정보 셀에 할당 
				this.assignPreprocesses(cells, equipPreprocesses);
			}
		}
	}

	/**
	 * 호기 존 내 할당 방식에 따라 로케이션 소팅하여 리턴
	 *
	 * @param domainId
	 * @param rackCd
	 * @return
	 */
	private List<Cell> sortCellBy(Long domainId, String rackCd) {
		if(ValueUtil.isNotEmpty(rackCd)) {
			Map<String, Object> params = ValueUtil.newMap("domainId,equipCds", domainId, ValueUtil.toList(rackCd));
			String sql = this.dasQueryStore.getCommonCellSortingQuery();
			return this.queryManager.selectListBySql(sql, params, Cell.class, 0, 0);
		} else {
			return null;
		}
	}
 
	/**
	 * 주문 가공 정보에 셀 정보 할당
	 * 
	 * @param cells
	 * @param preprocesses
	 * @return
	 */
	public int assignPreprocesses(List<Cell> cells, List<OrderPreprocess> preprocesses) {
		// 1. 소팅된 SKU 순서와 소팅된 로케이션 순서를 맞춰서 주문 가공 테이블에 업데이트한다.
		for(int i = 0 ; i < preprocesses.size() ; i++) {
			OrderPreprocess preprocess = preprocesses.get(i);
			Cell cell = cells.get(i);
			preprocess.setSubEquipCd(cell.getCellCd());
		}

		// 2. 주문 가공 정보에 CELL 코드 업데이트
		AnyOrmUtil.updateBatch(preprocesses, 100, "subEquipCd", "updatedAt");
		// 3. 결과 리턴
		return preprocesses.size();
	}
}
