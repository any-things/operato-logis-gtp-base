package operato.logis.gtp.base.service.rtn;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.gtp.base.GtpBaseConstants;
import operato.logis.gtp.base.query.store.GtpQueryStore;
import operato.logis.gtp.base.service.model.OrderGroup;
import operato.logis.gtp.base.service.model.RackCell;
import operato.logis.gtp.base.service.model.RtnPreprocessSummary;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.service.api.IPreprocessService;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query; 
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
		// TODO Auto-generated method stub
		//String condition
		
		List<OrderPreprocess> preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		if(ValueUtil.isEmpty(preprocesses)) {
			this.generatePreprocess(batch);
			preprocesses = this.queryManager.selectList(OrderPreprocess.class, query);
		}
		
		// 2. 주문 그룹 정보 조회, 3-2) 주문 가공 화면의 중앙 주문 그룹 리스트
		List<OrderGroup> groups = this.searchOrderGroupList(batch);
		// 3. 호기 정보 조회, 3-3) 주문 가공 화면의 우측 호기 리스트
		List<RackCell> regionCells = this.regionAssignmentStatus(batch);
		// 4. 호기별 물량 요약 정보, 3-4) 주문 가공 화면의 우측 상단 호기별 물량 요약 정보
		List<RtnPreprocessSummary> summaryByRegions = this.preprocessSummaryByRegions(batch);
		// 5. 상품별 물량 요약 정보, 3-5) 주문 가공 화면의 좌측 상단 Sku 별 물량 요약 정보
		Map<?, ?> summaryBySkus = this.preprocessSummary(batch, query);
		// 6. 리턴 데이터 셋
		return ValueUtil.newMap("regions,groups,preprocesses,summary,group_summary", regionCells, groups, preprocesses, summaryByRegions, summaryBySkus);
	
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
	public void resetPreprocess(JobBatch batch, boolean isRackReset) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int assignEquipLevel(JobBatch batch, String equipCds, List<OrderPreprocess> items, boolean automatically) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int assignSubEquipLevel(JobBatch batch, String equipType, String equipCd, List<OrderPreprocess> items) {
		// TODO Auto-generated method stub
		return 0;
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
	 * 작업 배치 별 주문 가공 정보에서 호기별로 상품 할당 상태를 조회하여 리턴
	 *
	 * @param batch
	 * @return
//	 */
	public List<RackCell> regionAssignmentStatus(JobBatch batch) {
		String sql = queryStore.getRtnRegionCellStatusQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,jobType,stageCd,activeFlag", batch.getDomainId(), batch.getId(), batch.getJobType(),batch.getStageCd(), 1);
		return this.queryManager.selectListBySql(sql, params, RackCell.class, 0, 0); 
	}

	/**
	 * 작업 배치 별 호기별 물량 할당 요약 정보를 조회하여 리턴
	 *
	 * @param batch 
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
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
}
