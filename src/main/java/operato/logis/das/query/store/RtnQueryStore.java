package operato.logis.das.query.store;

import org.springframework.stereotype.Component;

import xyz.anythings.sys.service.AbstractQueryStore;
import xyz.elidom.sys.SysConstants;

/**
 * 반품용 쿼리 스토어
 * 
 * @author shortstop
 */
@Component
public class RtnQueryStore extends AbstractQueryStore {

	@Override
	public void initQueryStore(String databaseType) {
		this.databaseType = databaseType;
		this.basePath = "operato/logis/das/query/" + this.databaseType + SysConstants.SLASH;
		this.defaultBasePath = "operato/logis/das/query/ansi/"; 
	}
	
	/**
	 * WMS I/F 테이블로 부터 반품 BatchReceipt 데이터를 조회
	 * 
	 * @return
	 */
	public String getWmsIfToReceiptDataQuery() {
		return this.getQueryByPath("batch/WmsIfToReceiptData");
	}
	
	/**
	 * WMS I/F 테이블로 부터  주문수신 완료된 데이터 변경('Y')
	 * 
	 * @return
	 */
	public String getWmsIfToReceiptUpdateQuery() {
		return this.getQueryByPath("batch/WmsIfToReceiptUpdate");
	}
	
	/**
	 * BatchReceipt 조회 - 상세 Item에 Order 타입이 있는 Case
	 *  
	 * @return
	 */
	public String getBatchReceiptOrderTypeStatusQuery() {
		return this.getQueryByPath("batch/BatchReceiptOrderTypeStatus");
	}
	
	/**
	 * 배치 Max 작업 차수 조회
	 *
	 * @return
	 */
	public String getFindMaxBatchSeqQuery() {
		return this.getQueryByPath("batch/FindMaxBatchSeq");
	}
	
	/**
	 * 주문 데이터로 부터 주문 가공 쿼리
	 *
	 * @return
	 */
	public String getRtnGeneratePreprocessQuery() {
		return this.getQueryByPath("preprocess/GeneratePreprocess");
	}
	
	/**
	 * 작업 배치 별 주문 그룹 리스트 가공 쿼리
	 *
	 * @return
	 */
	public String getOrderGroupListQuery() {
		return this.getQueryByPath("preprocess/OrderGroupList");
	}
	
	/**
	 * 작업 배치 별 주문 가공 정보에서 호기별로 상품 할당 상태를 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnRackCellStatusQuery() {
		return this.getQueryByPath("preprocess/RackCellStatus");
	}
	
	/**
	 * 작업 배치 별 호기별 물량 할당 요약 정보를 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnPreprocessSummaryQuery() {
		return this.getQueryByPath("preprocess/PreprocessSummary");
	}
	
	/**
	 * 작업 배치의 상품별 물량 할당 요약 정보 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnBatchGroupPreprocessSummaryQuery() {
		return this.getQueryByPath("preprocess/BatchGroupPreprocessSummary");
	}
	
	/**
	 * 작업 배치의 상품별 물량 할당 요약 정보 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnResetRackCellQuery() {
		return this.getQueryByPath("preprocess/ResetRackCell");
	}
	
	/**
	 * 작업 배치 주문 정보의 SKU 별 총 주문 개수와 주문 가공 정보(RtnPreprocess)의 SKU 별 총 주문 개수를 비교하여 같지 않은 SKU 정보만 조회하는 쿼리
	 *
	 * @return
	 */
	public String getRtnOrderPreprocessDiffStatusQuery() {
		return this.getQueryByPath("preprocess/OrderPreprocessDiffStatus");
	}
	
	/**
	 * 주문 가공 정보 호기 데이터 확인
	 *
	 * @return
	 */
	public String getRtnPreprocessRackSummaryQuery() {
		return this.getQueryByPath("preprocess/PreprocessRackSummary");
	}
	
	/**
	 * 병렬 호기인 경우 주문 가공 복제 쿼리
	 *
	 * @return
	 */
	public String getRtnPararellRackPreprocessCloneQuery() {
		return this.getQueryByPath("preprocess/PararellRackPreprocessClone");
	}
	
	/**
	 * 주문정보의 배치 ID 업데이트
	 *
	 * @return
	 */
	public String getRtnUpdateBatchOfOrdersQuery() {
		return this.getQueryByPath("preprocess/UpdateBatchOfOrders");
	}
	
	/**
	 * 작업 지시 시점에 작업 데이터 생성
	 *
	 * @return
	 */
	public String getRtnGenerateJobsByInstructionQuery() {
		return this.getQueryByPath("instruction/GenerateJobs");
	}
	
	/**
	 * 작업 지시를 위해 주문 가공 완료 요약 (주문 개수, 상품 개수, PCS) 정보 조회
	 *
	 * @return
	 */
	public String getRtnInstructionSummaryDataQuery() {
		return this.getQueryByPath("instruction/InstructionSummaryData");
	}
	
	/**
	 * 완료된 셀의 표시기를 강제 소등하기 위한 표시기 리스트 조회 
	 * 
	 * @return
	 */
	public String getRtnEndCellOffQuery() {
		return this.getQueryByPath("instruction/EndCellOffList");
	}
	
	/**
	 * 배치 병합시 메인 배치의 빈 셀 개수 조회 
	 * 
	 * @return
	 */
	public String getRtnEmptyCellCountForMergeQuery() {
		return this.getQueryByPath("instruction/EmptyCellCountForMerge");
	}
	
	/**
	 * 배치 병합시 메인 배치의 새로 필요한 셀 개수 조회 
	 * 
	 * @return
	 */
	public String getRtnNewCellCountForMergeQuery() {
		return this.getQueryByPath("instruction/NewCellCountForMerge");
	}
	
	/**
	 * 배치 병합시 병합 대상 배치의 새로 필요한 작업 셀 정보 조회 
	 * 
	 * @return
	 */
	public String getRtnNewWorkCellsForMergeQuery() {
		return this.getQueryByPath("instruction/NewWorkCellsForMerge");
	}
	
	/**
	 * 배치의 작업 정보 생성을 위한 작업 정보 생성 
	 * 
	 * @return
	 */
	public String getRtnNewJobInstancesByBatchQuery() {
		return this.getQueryByPath("instruction/NewJobInstancesByBatch");
	}
	
	/**
	 * 작업 마감을 위한 작업 데이터 요약 정보 조회
	 *
	 * @return
	 */
	public String getRtnBatchResultSummaryQuery() {
		return this.getQueryByPath("batch/BatchResultSummary");
	}
	
	/**
	 * 피킹 작업 현황 조회
	 * 
	 * @return
	 */
	public String getSearchPickingJobListQuery() {
		return this.getQueryByPath("pick/SearchPickingJobList");
	}
	
	/**
	 * Cell 할당을 위한 소팅 쿼리
	 *
	 * @return
	 */
	public String getCommonCellSortingQuery() {
		return this.getQueryByPath("etc/CellSorting");
	}

	/**
	 * 중분류 쿼리 
	 * 
	 * @return
	 */
	public String getRtnCategorizationQuery() {
		return this.getQueryByPath("pick/RtnCategorization");
	}

}
