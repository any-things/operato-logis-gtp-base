package operato.logis.gtp.base.service.rtn;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.gtp.base.GtpBaseConstants;
import operato.logis.gtp.base.query.store.GtpQueryStore;
import operato.logis.gtp.base.service.model.OrderGroup; 
import operato.logis.gtp.base.service.model.RackCells;
import operato.logis.gtp.base.service.model.RtnPreprocessSummary;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.service.api.IPreprocessService;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.sys.util.ValueUtil;

@Component("rtnPreprocessService")
public class RtnPreprocessService extends AbstractQueryService implements IPreprocessService{
	
	@Autowired
	private GtpQueryStore queryStore;
	
	@Override
	public List<OrderPreprocess> searchPreprocessList(JobBatch batch) {
		// TODO Auto-generated method stub
		return null;
	}

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
		List<RackCells> rackCells = this.regionAssignmentStatus(batch);
		// 4. 호기별 물량 요약 정보, 3-4) 주문 가공 화면의 우측 상단 호기별 물량 요약 정보
		List<RtnPreprocessSummary> summaryByRegions = this.preprocessSummaryByRegions(batch);
		// 5. 상품별 물량 요약 정보, 3-5) 주문 가공 화면의 좌측 상단 Sku 별 물량 요약 정보
		Map<?, ?> summaryBySkus = this.preprocessSummary(batch, query);
		// 6. 리턴 데이터 셋
		return ValueUtil.newMap("regions,groups,preprocesses,summary,group_summary", rackCells, groups, preprocesses, summaryByRegions, summaryBySkus);
	
	}

	@Override
	public int generatePreprocess(JobBatch batch) {
		// 1. 반품 2차 분류 여부 확인 
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

	@Override
	public int deletePreprocess(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<JobBatch> completePreprocess(JobBatch batch, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void resetPreprocess(JobBatch batch, boolean resetAll, List<String> equipCdList) {
		// TODO Auto-generated method stub
		
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
		Rack rack = Rack.findByRackCd(equipCds, true);
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
	 
		List<RackCells> rackCells = this.regionAssignmentStatus(batch, equipCds);

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

		// 5. 주문 가공별로 루프
		for(OrderPreprocess preprocess : items) {
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

		// 6. 주문 가공 정보 업데이트
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
	public List<RtnPreprocessSummary> preprocessSummaryByRegions(JobBatch batch) {
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
			ValueUtil.newMap("domainId,batchId,regionCd,orderGroup", batch.getDomainId(), batch.getId(), rackCd, classCd);

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
	 * @param isRegionReset
	 * @return
	 */
	private JobBatch checkJobBatchesForReset(String stageCd, String batchId, boolean isRackReset) {
		Long domainID = Domain.currentDomainId();
		JobBatch jobBatch = JobBatch.find(domainID, batchId, false,isRackReset);  
		
		if(!ValueUtil.isEqualIgnoreCase(jobBatch.getStatus(), JobBatch.STATUS_WAIT) &&
		   !ValueUtil.isEqualIgnoreCase(jobBatch.getStatus(), JobBatch.STATUS_READY)) {
			 // 주문 가공 대기, 작업 지시 대기 상태에서만 할당정보 리셋이 가능합니다.
	 		 throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_ONLY_ALLOWED_RESET_ASSIGN_ONLY");
		}

		Query condition = AnyOrmUtil.newConditionForExecution(domainID);
		condition.addFilter("batchId", jobBatch.getId());
//		condition.addFilter(new Filter("pickedQty", OrmConstants.GREATER_THAN, 0));

		// 분류 작업 시작한 개수가 있는지 체크
		if(this.queryManager.selectSize(OrderPreprocess.class, condition) > 0) {
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
	public List<RackCells> regionAssignmentStatus(JobBatch batch) {
		String sql = queryStore.getRtnRackCellStatusQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag", batch.getDomainId(), batch.getId(), batch.getJobType(),batch.getStageCd(), 1);
		return this.queryManager.selectListBySql(sql, params, RackCells.class, 0, 0); 
	}
	
	/**
	 * 작업 배치 별 주문 가공 정보에서 호기별로 SKU 할당 상태를 조회하여 리턴
	 *
	 * @param batch
	 * @param regionCds
	 * @return
	 */ 
	public List<RackCells> regionAssignmentStatus(JobBatch batch, String equipCds) {
		String sql = queryStore.getRtnRackCellStatusQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag,equipCds", batch.getDomainId(), batch.getId(), batch.getJobType(),batch.getStageCd(), 1, equipCds);
		return this.queryManager.selectListBySql(sql, params, RackCells.class, 0, 0);
	}

	
}
