package operato.logis.das.query.store;

import org.springframework.stereotype.Component;

import xyz.anythings.sys.service.AbstractQueryStore;
import xyz.elidom.sys.SysConstants;

/**
 * 출고용 쿼리 스토어
 * 
 * @author shortstop
 */
@Component
public class DasQueryStore extends AbstractQueryStore {
	
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
	 * WMS I/F 테이블로 부터 주문수신 완료된 데이터 변경('Y')
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
	public String getDasGeneratePreprocessQuery() {
		return this.getQueryByPath("preprocess/DasGeneratePreprocess");
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
	public String getDasRackCellStatusQuery() {
		return this.getQueryByPath("preprocess/DasRackCellStatus");
	}
	
	/**
	 * 작업 배치 별 호기별 물량 할당 요약 정보를 조회 쿼리
	 *
	 * @return
	 */
	public String getDasPreprocessSummaryQuery() {
		return this.getQueryByPath("preprocess/DasPreprocessSummary");
	}
	
	/**
	 * 작업 배치의 상품별 물량 할당 요약 정보 조회 쿼리
	 *
	 * @return
	 */
	public String getDasBatchGroupPreprocessSummaryQuery() {
		return this.getQueryByPath("preprocess/DasBatchGroupPreprocessSummary");
	} 
	
	/**
	 * 작업 배치의 상품별 물량 할당 요약 정보 조회 쿼리
	 *
	 * @return
	 */
	public String getDasResetRackCellQuery() {
		return this.getQueryByPath("preprocess/DasResetRackCell");
	} 
	
	/**
	 * 작업 배치 주문 정보의 SKU 별 총 주문 개수와 주문 가공 정보(OrderPreprocess)의 SKU 별 총 주문 개수를
	 * SKU 별로 비교하여 같지 않은 거래처의 정보만 조회하는 쿼리
	 *
	 * @return
	 */
	public String getDasOrderPreprocessDiffStatusQuery() {
		return this.getQueryByPath("preprocess/DasOrderPreprocessDiffStatus");
	} 
	
	/**
	 * 주문 가공 정보 호기 데이터 확인
	 *
	 * @return
	 */
	public String getDasPreprocessRackSummaryQuery() {
		return this.getQueryByPath("preprocess/DasPreprocessRackSummary");
	} 
	
	/**
	 * 병렬 호기인 경우 주문 가공 복제 쿼리
	 *
	 * @return
	 */
	public String getDasPararellRackPreprocessCloneQuery() {
		return this.getQueryByPath("preprocess/DasPararellRackPreprocessClone");
	} 
	
	/**
	 * 해당 배치의 주문 정보들의 호기
	 *
	 * @return
	 */
	public String getDasBatchIdOfOrderUpdateQuery() {
		return this.getQueryByPath("preprocess/DasBatchIdOfOrderUpdate");
	} 
	
	/**
	 * 작업 지시 시점에 작업 데이터 생성
	 *
	 * @return
	 */
	public String getDasGenerateJobsByInstructionQuery() {
		return this.getQueryByPath("instruction/DasGenerateJobs");
	} 
	
	/**
	 * 작업 지시를 위해 주문 가공 완료 요약 (거래처 개수, 상품 개수, PCS) 정보 조회
	 *
	 * @return
	 */
	public String getDasInstructionSummaryDataQuery() {
		return this.getQueryByPath("instruction/DasInstructionSummaryData");
	} 
	
	/**
	 * 작업 지시를 위한 작업 데이터 요약 정보 조회
	 *
	 * @return
	 */
	public String getDasJobInstancesSummaryDataQuery() {
		return this.getQueryByPath("instruction/DasJobInstancesSummaryData");
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

}
