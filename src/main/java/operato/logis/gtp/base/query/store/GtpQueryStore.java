package operato.logis.gtp.base.query.store;

import org.springframework.stereotype.Component;

import xyz.anythings.sys.service.AbstractQueryStore;
import xyz.elidom.sys.SysConstants;

/**
 * 반품 프로세스
 * 
 * @author shortstop
 */
@Component
public class GtpQueryStore extends AbstractQueryStore {

	@Override
	public void initQueryStore(String databaseType) {
		this.databaseType = databaseType;
		this.basePath = "operato/logis/gtp/base/query/" + this.databaseType + SysConstants.SLASH;
		this.defaultBasePath = "operato/logis/base/base/query/ansi/"; 
	}
	
	/*** BatchReceipt 관련 데이터 쿼리 ***/
	/**
	 * WMS I/F 테이블로 부터 반품 BatchReceipt 데이터를 조회 한다.
	 * @return
	 */
	public String getWmsIfToReceiptDataQuery() {
		return this.getQueryByPath("batch/WmsIfToReceiptData");
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
	 *WMS I/F 테이블로 부터  주문수신 완료된 데이터 변경('Y')
	 * 
	 * @return
	 */
	public String getWmsIfToReceiptUpdateQuery() {
		return this.getQueryByPath("batch/WmsIfToReceiptUpdate");
	} 
	
	/**
	 * 주문 데이터로 부터  주문 가공 쿼리
	 *
	 * @return
	 */
	public String getRtnGeneratePreprocessQuery(){
		return this.getQueryByPath("rtn/rtnGeneratePreprocess");
	}
	
	/**
	 * 작업 배치 별 주문 그룹 리스트 가공 쿼리
	 *
	 * @return
	 */
	public String getOrderGroupListQuery() {
		return this.getQueryByPath("rtn/orderGroupList");
	}
	
	
	/**
	 * 작업 배치 별 주문 가공 정보에서 호기별로 상품 할당 상태를 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnRackCellStatusQuery() {
		return this.getQueryByPath("rtn/rtnRackCellStatus");
	}
	
	/**
	 * 작업 배치 별 호기별 물량 할당 요약 정보를 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnPreprocessSummaryQuery() {
		return this.getQueryByPath("rtn/rtnPreprocessSummary");
	}
	
	/**
	 *  작업 배치의 상품별 물량 할당 요약 정보 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnBatchGroupPreprocessSummaryQuery() {
		return this.getQueryByPath("rtn/rtnBatchGroupPreprocessSummary");
	} 
	
	/**
	 *  작업 배치의 상품별 물량 할당 요약 정보 조회 쿼리
	 *
	 * @return
	 */
	public String getRtnResetRackCellQuery() {
		return this.getQueryByPath("rtn/rtnResetRackCell");
	} 
	
	/**
	 * 작업 배치 주문 정보의 SKU 별 총 주문 개수와 주문 가공 정보(RtnPreprocess)의 SKU 별 총 주문 개수를
	 * SKU 별로 비교하여 같지 않은 거래처의 정보만 조회하는 쿼리
	 *
	 * @return
	 */
	public String getRtnOrderPreprocessDiffStatusQuery() {
		return this.getQueryByPath("rtn/rtnOrderPreprocessDiffStatus");
	} 
	
	/**
	 * 주문 가공 정보 호기 데이터 확인
	 *
	 * @return
	 */
	public String getRtnPreprocessRackSummaryQuery() {
		return this.getQueryByPath("rtn/rtnPreprocessRackSummary");
	} 
	
	/**
	 * Cell 할당을 위한 소팅 쿼리
	 *
	 * @return
	 */
	public String getCommonCellSortingQuery() {
		return this.getQueryByPath("rtn/commonCellSorting");
	} 
	
	/**
	 * 병렬 호기인 경우 주문 가공 복제 쿼리
	 *
	 * @return
	 */
	public String getRtnPararellRackPreprocessCloneQuery() {
		return this.getQueryByPath("rtn/rtnPararellRackPreprocessClone");
	} 
	
	
	
	
}
