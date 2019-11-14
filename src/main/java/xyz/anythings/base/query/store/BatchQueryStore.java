package xyz.anythings.base.query.store;

import org.springframework.stereotype.Component;

/**
 * 작업 배치 관련 쿼리 스토어 
 * - 주문 수신, 배치 작업 현황, 배치 마감 관련 쿼리 관리
 * 
 * @author shortstop
 */
@Component
public class BatchQueryStore extends AbstractQueryStore {

	/**
	 * 창고 전체의 최근 3개월 작업 진행율 쿼리
	 * 
	 * @return
	 */
	public String getLatestMonthlyRateQuery() {
		return this.getQueryByPath("batch/LatestMonthlyRate");
	}
	
	/**
	 * 스테이지 별 일일 작업 진행율 쿼리
	 * 
	 * @return
	 */
	public String getDailyProgressRateQuery() {
		return this.getQueryByPath("batch/DailyPrograssRate");
	}
	
	/**
	 * 작업 배치 그룹 범위 안에 있는 작업 데이터 중에서 넘어온 배치 정보와 상품 정보로 부터 SKU 정보를 조회
	 * 
	 * @return
	 */
	public String getSearchSkuInBatchGroupQuery() {
		return this.getQueryByPath("batch/SearchSkuInBatchGroup");
	}
	
	/**
	 * 작업 배치 범위 안에 있는 작업 데이터 중에서 넘어온 배치 정보와 상품 정보로 부터 SKU 정보를 조회
	 * 
	 * @return
	 */
	public String getSearchSkuInBatchQuery() {
		return this.getQueryByPath("batch/SearchSkuInBatch");
	}
	
	/**
	 * 배치 그룹내 배치에 대해서 끝나지 않은 정보가 있는지 체크
	 * 
	 * @return
	 */
	public String getCheckCloseBatchGroupQuery() {
		return this.getQueryByPath("batch/CheckCloseBatchGroup");
	}
	
	/**
	 * Rack 타입 배치 진행율 쿼리 
	 * @return
	 */
	public String getRackBatchProgressRateQuery() {
		return this.getQueryByPath("batch/RackBatchProgressRate");
	}
	
	/**
	 * Rack DPS 투입 가능 박스 수 쿼리 
	 * @return
	 */
	public String getRackDpsBatchInputableBoxQuery() {
		return this.getQueryByPath("batch/RackDpsBatchInputableBox");
	}
	
	/**
	 * Rack DPS 투입 리스트 쿼리 
	 * @return
	 */
	public String getRackDpsBatchInputListQuery() {
		return this.getQueryByPath("batch/RackDpsBatchInputList");
	}
	
	/*** BatchReceipt 관련 데이터 쿼리 ***/
	/**
	 * WMS I/F 테이블로 부터 BatchReceipt 데이터를 조회 한다.
	 * @return
	 */
	public String getWmsIfToReceiptDataQuery() {
		return this.getQueryByPath("batch/WmsIfToReceiptData");
	}
	
	/**
	 * WMS I/F 테이블로 부터 반품 BatchReceipt 데이터를 조회 한다.
	 * @return
	 */
	public String getWmsIfToReturnReceiptDataQuery() {
		return this.getQueryByPath("batch/WmsIfToReturnReceiptData");
	}
	
	/**
	 * BatchReceipt 조회
	 * 상세 Item 에 Order 타입이 있는 Case 
	 * @return
	 */
	public String getBatchReceiptOrderTypeStatusQuery() {
		return this.getQueryByPath("batch/BatchReceiptOrderTypeStatus");
	}
	
	/**
	 * 마지막 투입 작업 조회 
	 * @return
	 */
	public String getLatestJobInputQuery() {
		return this.getQueryByPath("batch/LastestJobInput");
	}
	
	/**
	 * 다음 맵핑할 작업 정보 조회 
	 * @return
	 */
	public String getFindNextMappingJobQuery() {
		return this.getQueryByPath("batch/FindNextMappingJob");
	}
	
	/**
	 * 배치 instances 데이터 에서 Input 데이터 생성 쿼리 
	 * @return
	 */
	public String getRackDpsBatchNewInputDataQuery() {
		return this.getQueryByPath("batch/RackDpsBatchNewInputData");
	}
	
	/**
	 * instance 테이블에 boxId 및 seq 정보 update 쿼리 
	 * @return
	 */
	public String getRackDpsBatchMapBoxIdAndSeqQuery() {
		return this.getQueryByPath("batch/RackDpsBatchMapBoxIdAndSeq");
	}
	
	/**
	 * Rack DPS 작업의 투입 탭 리스트 쿼리 
	 * @return
	 */
	public String getRackDpsBatchBoxInputTabsQuery() {
		return this.getQueryByPath("batch/RackDpsBatchBoxInputTabs");
	}
}
