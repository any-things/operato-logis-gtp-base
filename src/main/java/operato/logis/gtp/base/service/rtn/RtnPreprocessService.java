package operato.logis.gtp.base.service.rtn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component; 
import operato.logis.gtp.base.GtpBaseConstants;
import operato.logis.gtp.base.query.store.GtpQueryStore;
import operato.logis.gtp.base.service.model.OrderGroup; 
import operato.logis.gtp.base.service.model.RackCells;
import operato.logis.gtp.base.service.model.RtnPreprocessStatus;
import operato.logis.gtp.base.service.model.RtnPreprocessSummary;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.service.api.IPreprocessService;
import xyz.anythings.base.util.LogisBaseUtil;
import xyz.anythings.sys.AnyConstants;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.sys.entity.User;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.sys.util.ValueUtil;
import xyz.elidom.util.BeanUtil;

@Component("rtnPreprocessService")
public class RtnPreprocessService extends AbstractQueryService implements IPreprocessService{
	
	@Autowired
	private GtpQueryStore queryStore;
	
	@Override
	public Map<String, ?> buildPreprocessSet(JobBatch batch, Query query) {
		
		List<OrderPreprocess> preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		if(ValueUtil.isEmpty(preprocesses)) {
			this.generatePreprocess(batch);
			preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		}
		
		// 2. 주문 그룹 정보 조회, 3-2) 주문 가공 화면의 중앙 주문 그룹 리스트
		List<OrderGroup> groups = this.searchOrderGroupList(batch);
		// 3. 호기 정보 조회, 3-3) 주문 가공 화면의 우측 호기 리스트
		List<RackCells> rackCells = this.rackAssignmentStatus(batch);
		// 4. 호기별 물량 요약 정보, 3-4) 주문 가공 화면의 우측 상단 호기별 물량 요약 정보
		List<RtnPreprocessSummary> summaryByRacks = this.preprocessSummaryByRacks(batch);
		// 5. 상품별 물량 요약 정보, 3-5) 주문 가공 화면의 좌측 상단 Sku 별 물량 요약 정보
		Map<?, ?> summaryBySkus = this.preprocessSummary(batch, query);
		// 6. 리턴 데이터 셋
		return ValueUtil.newMap("regions,groups,preprocesses,summary,group_summary", rackCells, groups, preprocesses, summaryByRacks, summaryBySkus);
	
	}

	@Override
	public int deletePreprocess(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<JobBatch> completePreprocess(JobBatch batch, Object... params) {
		// 1. 주문 가공 정보가 존재하는지 체크
		this.beforeCompletePreprocess(batch, false);
		
		// 주문 가공 완료 처리 주문 가공 배치 그룹
		List<JobBatch> batchGroups = new ArrayList<JobBatch>();
	
		// 2. 무오더 반품 유형이면 작업 지시 완료
		if(AnyConstants.isNoOrderJobType(batch.getJobType())) {
			batchGroups.add(batch);
			batch.setStatus(JobBatch.STATUS_READY);
			this.queryManager.update(batch, "status");
			BeanUtil.get(RtnInstructionService.class).instructBatch(batch, null);

		// 3. 일반 반품 유형이면
		} else {
			// 3-1. 주문 가공 정보 호기 요약 정보 확인
			Map<String,Object> processParams =
				ValueUtil.newMap("domainId,batchId,batchGroupId", batch.getDomainId(), batch.getId(),batch.getBatchGroupId());
			String sql = queryStore.getRtnPreprocessRackSummaryQuery();
			List<JobBatch> summaryBatchGroups = this.queryManager.selectListBySql(sql, processParams, JobBatch.class, 0, 0);
			
			// 3-2. 작업 차수 생성
			Integer jobBatchSeq = batch.getJobSeq();
			if(ValueUtil.isEmpty(jobBatchSeq) || jobBatchSeq == 0) {
				jobBatchSeq = this.queryManager.selectBySql("select nvl(max(job_batch_seq), 0) from tb_job_batch where domain_id = :domainId and batch_group_id = :batchGroupId ", processParams, Integer.class);
				jobBatchSeq += 1;
			}
	
			processParams.put("jobBatchSeq", jobBatchSeq);
			// 3-3. 주문 가공 완료 처리
			for(JobBatch subBatch : summaryBatchGroups) {
				if(ValueUtil.isNotEmpty(subBatch.getEquipCd())) { 
					subBatch.setId(batch.getId());
					subBatch.setStageCd(batch.getStageCd());
					subBatch.setDomainId(batch.getDomainId());
					subBatch.setJobType(batch.getJobType());
					subBatch.setJobSeq(jobBatchSeq); 
					this.completePreprocessingByBatch(subBatch, processParams);
				}
			}
			
			batchGroups = summaryBatchGroups;
		}

		// 4. 주문 가공 이벤트 전송
//		PreprocessEvent event = new PreprocessEvent(batch.getDomainId(), batch, null);
//		BeanUtil.get(EventPublisher.class).publishEvent(event);

		// 5. 주문 가공 완료 처리한 배치 리스트 리턴
		return batchGroups;

	}

	@Override
	public void resetPreprocess(JobBatch batch, boolean resetAll, List<String> equipCdList) {
		//배치 리셋을 위한 배치 정보 체크
		this.checkJobBatchesForReset(batch, batch.getId());
		
		//할당 정보 리셋 
		if(resetAll) {
			this.generatePreprocess(batch);
		}else {
			String qry = queryStore.getRtnResetRackCellQuery();
			this.queryManager.executeBySql(qry, ValueUtil.newMap("domainId,batchId,equipCds", batch.getDomainId(), batch.getId(),equipCdList));	
		}
	}

	@Override
	public int assignEquipLevel(JobBatch batch, String equipCds, List<OrderPreprocess> items, boolean automatically) {
 		// 1. 거래처 정보 Empty Check
		if(ValueUtil.isEmpty(items)) {
			throw new ElidomRuntimeException("There is no customers!");
		}
		
		// 2. 호기 지정
		if(automatically) {
			assignRackByAuto(batch, equipCds, items);
		}else {
			assignRackByManual(batch, equipCds, items);
			
		}
		
		return items.size(); 
	}
	
	
	@Override
	public int assignSubEquipLevel(JobBatch batch, String equipType, String equipCd, List<OrderPreprocess> items) {
		// TODO Auto-generated method stub
		
		return 0;
	}
	
	/**
	 * 주문 가공 정보에 호기 지정
	 *
	 * @param batch		
	 * @param equipCds
	 * @param items
	 * @return
	 */
	public int assignRackByManual(JobBatch batch, String equipCds, List<OrderPreprocess> items ) {
		// 1. 이미 호기가 지정 되어 있는 거래처 개수 조회
		Rack rack = Rack.findByRackCd(batch.getDomainId(),equipCds, false);
		Map<String, Object> params = ValueUtil.newMap("batchId,equipCd", batch.getId(), equipCds);
		int assignedCount = this.queryManager.selectSize(OrderPreprocess.class, params);

		// 2. 새로 호기에 할당할 거래처의 개수 합이 호기의 셀 보다 많을 때 예외 발생
		int cellCount = rack.validLocationCount();
		if(cellCount < assignedCount + items.size()) {
			// 호기의 빈 로케이션보다 할당해야 하는 거래처 수가 많습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_MISMATCH_CUST_AND_EMPTY_LOCATION");
		}
		
		// 3. 호기 지정
		for(OrderPreprocess preprocess : items) {
			preprocess.setEquipCd(rack.getRackCd());
			preprocess.setEquipNm(rack.getRackNm());
		}
		
		// 4. 호기에 배치ID 매핑
		rack.setBatchId(batch.getId()); 
		rack.setStatus(JobBatch.STATUS_WAIT);
		this.queryManager.update(rack, "batchId","status");
		
		
		//5. OrderPreprocess 호기 지정 Update
		AnyOrmUtil.updateBatch(items, 100, "equipCd", "equipNm", "updatedAt");
		 
		return 0;
	}

	/**
	 * 주문 가공 정보에 호기 자동 할당 
	 *
	 * @param batch
	 * @return
	 */
	public int assignRackByAuto(JobBatch batch, String equipCds, List<OrderPreprocess> items) {
		// 1. 주문 가공 정보 호기 매핑 리셋
		String qry = queryStore.getRtnResetRackCellQuery();
		this.queryManager.executeBySql(qry, ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId()));
		// 2. 호기 리스트 조회
		int skuCount = items.size();
	 
		List<RackCells> rackCells = this.rackAssignmentStatus(batch);
		
		// 3. 상품 개수와 호기의 사용 가능한 셀 개수를 비교해서
		int rackCapa = 0;
		for(RackCells rackCell : rackCells) {
			rackCapa += rackCell.getRemainCells();
		}

		int removalCount = skuCount - rackCapa;
		// 4. 거래처 개수가 호기의 사용 가능 셀 개수보다 크다면 큰 만큼 거래처 리스트에서 삭제
		if(removalCount > 0) {
			// 거래처 리스트를 호기의 사용 가능 셀 만큼만 남기고 나머지는 제거
			for(int i = 0 ; i < removalCount ; i++) {
				items.remove(items.size() - 1);
			}
		}

		boolean idxGoForward = true;
		int rackIdx = 0;
		int rackEndIdx = rackCells.size();
		String checkRack = "";
		

		List<Rack> rackList = new ArrayList<Rack>();
		
		// 5. 주문 가공별로 루프
		for(OrderPreprocess preprocess : items) {
			
			if(preprocess.getId().isEmpty()) break;
			
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
			
			//호기에 배치ID 매핑
			if(!checkRack.equals(rackCell.getRackCd())) {
				checkRack = rackCell.getRackCd();
				
				Rack rack= Rack.findByRackCd(batch.getDomainId(), checkRack, true);
				rack.setBatchId(preprocess.getBatchId());
				rack.setStatus(JobBatch.STATUS_WAIT); 
				this.queryManager.update(rack, "batchId","status");
				
			}
		}
		  
		// 7. 주문 가공 정보 업데이트
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
		String sql = queryStore.getOrderGroupListQuery();
		return this.queryManager.selectListBySql(sql, ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId()), OrderGroup.class, 0, 0);
	}
	

	/**
	 * 작업 배치 별 호기별 물량 할당 요약 정보를 조회하여 리턴
	 *
	 * @param batch 
	 * @return
	 */
	public List<RtnPreprocessSummary> preprocessSummaryByRacks(JobBatch batch) {
		String sql = queryStore.getRtnPreprocessSummaryQuery();
		Map<String, Object> params = ValueUtil.newMap("batchId", batch.getId());
		return this.queryManager.selectListBySql(sql, params,RtnPreprocessSummary.class, 0, 0);
	}
	
	/**
	 * 작업 배치의 상품별 물량 할당 요약 정보 조회
	 * 
	 * @param batch
	 * @param query
	 * @return
	 */
	public Map<?, ?> preprocessSummary(JobBatch batch, Query query) {
		String rackCd = AnyValueUtil.getFilterValue(query, "rack_cd");
		String classCd = AnyValueUtil.getFilterValue(query, "class_cd");

		if(AnyValueUtil.isEmpty(rackCd)) rackCd = GtpBaseConstants.ALL_CAPITAL_STR;
		if(AnyValueUtil.isEmpty(classCd)) classCd = GtpBaseConstants.ALL_CAPITAL_STR;

		Map<String,Object> params =
			ValueUtil.newMap("domainId,batchId,rackCd,orderGroup", batch.getDomainId(), batch.getId(), rackCd, classCd);

		if(AnyValueUtil.isEqualIgnoreCase(classCd, "is blank")) params.remove("classCd");
		if(AnyValueUtil.isEqualIgnoreCase(rackCd, "is blank")) params.remove("rackCd");

		String sql = queryStore.getRtnBatchGroupPreprocessSummaryQuery();
		return this.queryManager.selectBySql(sql, params, Map.class);
	}
	
	/**
	 * 배치 리셋을 위한 배치 정보 체크
	 *
	 * @param domainId
	 * @param batchId 
	 * @return
	 */
	private JobBatch checkJobBatchesForReset(JobBatch jobBatch, String batchId) {

		if(!ValueUtil.isEqualIgnoreCase(jobBatch.getStatus(), JobBatch.STATUS_WAIT) &&
		   !ValueUtil.isEqualIgnoreCase(jobBatch.getStatus(), JobBatch.STATUS_READY)) {
			 // 주문 가공 대기, 작업 지시 대기 상태에서만 할당정보 리셋이 가능합니다.
	 		 throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_ONLY_ALLOWED_RESET_ASSIGN_ONLY");
		}

		Query condition = AnyOrmUtil.newConditionForExecution(jobBatch.getDomainId());
		condition.addFilter("batchId", jobBatch.getId());
		condition.addFilter(new Filter("pickedQty", OrmConstants.GREATER_THAN, 0));

		// 분류 작업 시작한 개수가 있는지 체크
		if(this.queryManager.selectSize(Order.class, condition) > 0) {
			// 분류 작업시작 이후여서 할당정보 리셋 불가능합니다.
			throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_NOT_ALLOWED_RESET_ASSIGN_AFTER_START_JOB");
		}

		return jobBatch;
	}
	
	

	/**
	 * 작업 배치 별 주문 가공 정보에서 호기별로 상품 할당 상태를 조회하여 리턴
	 *
	 * @param batch
	 * @return
	 */
	public List<RackCells> rackAssignmentStatus(JobBatch batch) {
		String sql = queryStore.getRtnRackCellStatusQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag", batch.getDomainId(), batch.getId(), batch.getJobType(),batch.getStageCd(), 1);
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
		String sql = queryStore.getRtnRackCellStatusQuery(); 
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag,equipCds", batch.getDomainId(), batch.getId(), batch.getJobType(),batch.getStageCd(), 1, equipCds);
		return this.queryManager.selectListBySql(sql, params, RackCells.class, 0, 0);
	}


	public int generatePreprocess(JobBatch batch) {
		// 1. 반품 2차 분류 여부 확인 
		Query query = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		query.addFilter("batchId", batch.getId());
		
		this.queryManager.deleteList(OrderPreprocess.class, query);
		
		String sql = queryStore.getRtnGeneratePreprocessQuery();
		Map<String, Object> condition = ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		List<OrderPreprocess> preprocessList = this.queryManager.selectListBySql(sql, condition, OrderPreprocess.class, 0, 0);

		// 2. 주문 가공 데이터를 추가
		int generatedCount = ValueUtil.isNotEmpty(preprocessList) ? preprocessList.size() : 0;
		if(generatedCount > 0) {
			this.queryManager.insertBatch(preprocessList);
		}

		// 3. 결과 리턴
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
		int preprocessCount = batch.preprocessCountByCondition(null, null, null);

		if(preprocessCount == 0) {
			throw new ElidomRuntimeException("No preprocess data.");
		}

		// 3. 주문에서 서머리한 정보와 주문 가공 정보 개수가 맞는지 체크
		if(this.checkOrderPreprocessDifferent(batch) > 0) {
			// 수신한 주문 개수와 주문가공 개수가 다릅니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_MISMATCH_RECEIVED_AND_PREPROCESSED");
		}

		// 4. 호기지정이 안 된 SKU가 존재하는지 체크
		if(checkRackAssigned) {
			int notAssignedCount = batch.preprocessCountByCondition("Cd", "is_blank", OrmConstants.EMPTY_STRING);
			if(notAssignedCount > 0) {
				// 호기지정이 안된 상품이 " + notAssignedCount + "개 있습니다.
				throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_EXIST_NOT_ASSIGNED_SKUS", ValueUtil.toList("" + notAssignedCount));
			}
		}
	}
	
	/**
	 * 배치 정보의 주문 정보와 주문 가공 정보의 개수가 일치하지 않는지 체크
	 *
	 * @param batch
	 * @return
	 */
	private int checkOrderPreprocessDifferent(JobBatch batch) {
		// 1. 주문 테이블 기준으로 작업 배치 테이블에서 Sku 별로 총 주문 PCS가 다른 Sku 리스트를 구한다.
		List<RtnPreprocessStatus> diffByOrder = this.rtnOrderPreprocessDiffStatus(batch, "order");
		// 2. 작업 배치 테이블 기준으로 주문 테이블과 Sku 별로 총 주문 PCS가 다른 Sku 리스트를 구한다.
		List<RtnPreprocessStatus> diffByPreprocess = this.rtnOrderPreprocessDiffStatus(batch,"preprocess");
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
	public List<RtnPreprocessStatus> rtnOrderPreprocessDiffStatus(JobBatch batch,String diffStandard) {
		String outerJoinDiretion = ValueUtil.isEqualIgnoreCase(diffStandard, "order") ? "LEFT" : "RIGHT";
		String sql = this.queryStore.getRtnOrderPreprocessDiffStatusQuery();
		
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,outerJoinDiretion", batch.getDomainId(), batch.getId(),outerJoinDiretion);
		return BeanUtil.get(IQueryManager.class).selectListBySql(sql, params, RtnPreprocessStatus.class, 0, 0);
	}
	 
	/**
	 * 작업 서브 배치 별 주문 가공 완료 처리
	 *
	 * @param subBatch
	 * @param params
	 */
	@SuppressWarnings("unchecked")
	private void completePreprocessingByBatch(JobBatch subBatch, Map<String,Object> params) {
		// 주문 가공 정보 조회
		List<OrderPreprocess> items = (List<OrderPreprocess>)this.searchPreprocessList(subBatch);

		if(ValueUtil.isNotEmpty(items)) {
			// 호기 리스트 조회
			List<RackCells> rackCells = this.rackAssignmentStatus(subBatch, ValueUtil.newStringList(subBatch.getEquipCd()));

			if(ValueUtil.isNotEmpty(rackCells)) {
				// 물량이 많은 상품순으로 작업 존 리스트를 왔다갔다 하면서 로케이션 지정을 자동으로 한다.
				int count = this.assignCells(subBatch, rackCells, items);
			} else {
				// 호기[" + subBatch.getCd() + "]를 사용할 수 없습니다.
				throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_CANNOT_USE_", ValueUtil.toList(subBatch.getEquipCd()));
			}
		}
	}

	public List<OrderPreprocess> searchPreprocessList(JobBatch batch) {
 		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addSelect("id","batchId","comCd","cellAssgnCd","cellAssgnNm","equipCd","equipNm","subEquipCd","skuQty","totalPcs");
		condition.addFilter("batchId", batch.getId());
		//condition.addFilter(new Filter("equipCd", "is_not_null", SysConstants.EMPTY_STRING));
		condition.addFilter("equipCd", batch.getEquipCd());
		
		condition.addOrder("totalPcs", false);
		return this.queryManager.selectList(OrderPreprocess.class, condition);
	}
 	 
	
 	/**
	 * 상품별 로케이션 할당
	 *
	 * @param batch
	 * @param rack
	 * @param preprocesses
	 * @return
	 */
 	public int assignCells(JobBatch batch, String rack, List<OrderPreprocess> preprocesses) {
		if(ValueUtil.isEmpty(preprocesses)) return 0;

		List<RackCells> rackCells = this.checkAssignmentToRacks(batch, rack, preprocesses.size());
		return this.assignCells(batch,rackCells, preprocesses);
	}
 	
	/**
	 * 상품별 로케이션 할당
	 *
	 * @param batch
	 * @param rackCells
	 * @param preprocesses
	 * @return
	 */
	private int assignCells(JobBatch batch, List<RackCells> rackCells, List<OrderPreprocess> preprocesses) {

		// 1. SKU별 로케이션을 호기 리스트 범위 내에 할당  
		List<String> rackCdList = AnyValueUtil.filterValueListBy(rackCells, "rackCd");
		int result = 0;

		if(ValueUtil.isNotEmpty(rackCdList)) {
			// 2. 호기 내에서 할당 옵션에 따라 로케이션 정보를 소팅한다.
			List<Cell> cells = this.sortCellBy(batch.getDomainId(), rackCdList);

			if(ValueUtil.isNotEmpty(cells)) {
				result = this.assignPreprocesses(batch, rackCells, cells, preprocesses);
				// 3. JobBatch 상태 업데이트
				batch.setStatus(JobBatch.STATUS_READY);
				this.queryManager.update(batch, "status");
			}
		}

		// 6. 최종 결과 리턴
		return result;
	}

	/**
	 * 호기 존 내 할당 방식에 따라 로케이션 소팅하여 리턴
	 *
	 * @param domainId
	 * @param rackCdList
	 * @return
	 */
	private List<Cell> sortCellBy(Long domainId, List<String> rackCdList) {
		if(ValueUtil.isNotEmpty(rackCdList)) {
			Map<String,Object> params = ValueUtil.newMap("domainId,equipCds", domainId, rackCdList);
			String sql = queryStore.getCommonCellSortingQuery();
			return this.queryManager.selectListBySql(sql, params, Cell.class, 0, 0);
		} else {
			return null;
		}
	}
 
	public int assignPreprocesses(JobBatch batch, List<RackCells> rackCells, List<Cell> cells, List<OrderPreprocess> preprocesses) {
		
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


	/**
	 * 호기 지정 전 validation
	 * rackCds 정보로 배치별 호기 상태 정보를 리턴. 이 때 상품 수 보다 호기 사용 셀 개수가 큰 지 validation
	 *
	 * @param batch
	 * @param rackCds
	 * @param customerItemCount
	 * @return
	 */
	private List<RackCells> checkAssignmentToRacks(JobBatch batch, String equipCds, int skuItemCount) {
		List<String> rackCdList = Arrays.asList(equipCds.split(SysConstants.COMMA));
		return this.rackAssignmentStatus(batch, rackCdList);
	}
	

	

}
