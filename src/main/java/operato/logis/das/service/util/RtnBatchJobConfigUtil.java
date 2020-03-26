package operato.logis.das.service.util;

import operato.logis.das.DasConfigConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.service.util.BatchJobConfigUtil;

/**
 * 반품 관련 작업 배치 관련 설정 프로파일
 * 
 * 작업 설정 프로파일 컨셉
 * 1. 스테이지마다 기본 설정 프로파일이 존재하고 기본 설정 프로파일은 default_flag = true인 것이다.
 * 2. job.cmm으로 시작하는 항목은 모두 기본 설정 프로파일에 추가가 이미 되어 있다. -> 없으면 해당 설정 항목 조회시 에러 발생해야 함
 * 3. default_flag가 false인 설정 프로파일은 기본 설정 프로파일의 모든 값을 복사하여 가지고 있어서 조회시 자기가 가진 정보로 조회한다. 없으면 기본 설정을 찾는다.
 * 4. 작업 배치에서 조회할 내용이 아닌 설정은 (성격상 작업 배치가 결정되지 않은 시점에 필요한 설정) Setting 정보에 존재한다.
 * 
 * 반품 작업 설정 항목
 *  - job.rtn.cell-sku.mapping.point			셀 - SKU 매핑 시점 (P:주문가공시, A:분류 시)		A
 *  - job.rtn.preproces.cell.mapping.field		셀에 할당할 대상 필드 (매장, 상품, 주문번호 …) 		sku_cd
 *   
 * @author shortstop
 */
public class RtnBatchJobConfigUtil extends BatchJobConfigUtil {

	/**
	 * 셀 - SKU 매핑 시점 (P: 주문 가공시, A: 분류 시)
	 * 
	 * @param batch
	 * @return
	 */
	public static String getCellSkuMappingPoint(JobBatch batch) {
		// job.rtn.cell-sku.mapping.point						
		return getConfigValue(batch, DasConfigConstants.RTN_CELL_SKU_MAPPING_POINT, DasConfigConstants.RTN_CELL_SKU_MAPPING_POINT_PREPROCESS);
	}
	
	/**
	 * 셀에 할당할 대상 필드 (매장, 상품, 주문번호 …)
	 * 
	 * @param batch
	 * @return
	 */
	public static String getBoxMappingTargetField(JobBatch batch) {
		// job.rtn.preproces.cell.mapping.field
		return getConfigValue(batch, DasConfigConstants.RTN_PREPROCESS_CELL_MAPPING_FIELD, "sku_cd");
	}

}
