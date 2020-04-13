package operato.logis.das;

import xyz.anythings.base.LogisConfigConstants;

/**
 * DAS 용 설정용 상수 정의
 * 
 * @author shortstop
 */
public class DasConfigConstants extends LogisConfigConstants {

	/**
	 * 셀 - 박스 매핑 시점 (P: 주문 가공시)
	 */
	public static final String DAS_CELL_BOX_MAPPING_POINT_PREPROCESS = "P";
	/**
	 * 셀 - 박스 매핑 시점 (A: 분류 시)
	 */
	public static final String DAS_CELL_BOX_MAPPING_POINT_ASSORTING = "A";
	
	/**
	 * 다음 작업 처리 방식 (relay)
	 */
	public static final String DAS_NEXT_JOB_PROCESS_METHOD_RELAY = "relay";
	/**
	 * 다음 작업 처리 방식 (event)
	 */
	public static final String DAS_NEXT_JOB_PROCESS_METHOD_EVENT = "event";
	
	/**
	 * DAS 중분류 화면에서 표시 수량 (fix : 고정)
	 */
	public static final String DAS_CATEGORIZATION_QTY_MODE_FIX = "fix";
	/**
	 * DAS 중분류 화면에서 표시 수량 (filter : 처리 수량 제외 계산)
	 */
	public static final String DAS_CATEGORIZATION_QTY_MODE_FILTER = "filter";

}
