package operato.logis.das.service.util;

import operato.logis.das.DasConfigConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.service.util.BatchJobConfigUtil;

/**
 * 출고 관련 작업 배치 관련 설정 프로파일
 * 
 * 작업 설정 프로파일 컨셉
 * 1. 스테이지마다 기본 설정 프로파일이 존재하고 기본 설정 프로파일은 default_flag = true인 것이다.
 * 2. job.cmm으로 시작하는 항목은 모두 기본 설정 프로파일에 추가가 이미 되어 있다. -> 없으면 해당 설정 항목 조회시 에러 발생해야 함
 * 3. default_flag가 false인 설정 프로파일은 기본 설정 프로파일의 모든 값을 복사하여 가지고 있어서 조회시 자기가 가진 정보로 조회한다. 없으면 기본 설정을 찾는다.
 * 4. 작업 배치에서 조회할 내용이 아닌 설정은 (성격상 작업 배치가 결정되지 않은 시점에 필요한 설정) Setting 정보에 존재한다.
 * 
 * 출고 작업 설정 항목
 *  - job.das.cell-boxid.mapping.point			로케이션과 박스 ID를 매핑하는 시점 - 선 매핑이냐 후 매핑이냐 여부		S    --> DAS에서 후 매핑은 불가능, 이 옵션은 선 매핑, 후 매핑이 아니고 박스 매핑을 할 지 말지 여부 (안 한다고 하면 이는 Fullbox 시에 자동으로 송장으로 처리되어야 함)
 *  - job.das.input.check.weight.enabled		키오스크에서 상품 투입시에 상품 중량 체크 여부 						true
 *  - job.das.next-job.event.method				다음 작업 처리 방식 (relay / event)							relay
 *  - job.das.middleassort.display.qty.mode		DAS KIOSK 중분류 화면에서 표시 수량 (fix/filter)				filter
 *  - job.das.middleassort.rack.sort.ascending	DAS KIOSK 중분류 화면에서 호기 정렬 옵션 						false
 *  - job.das.pick.relay.max.no					표시기에 표시할 릴레이 번호 최대 번호 (최대 번호 이후 다시 1로)		99
 *  - job.das.preprocess.cell.mapping.field		셀에 할당할 대상 필드 (매장, 상품, 주문번호…) 					shop_cd
 * 
 * @author shortstop
 */
public class DasBatchJobConfigUtil extends BatchJobConfigUtil {

	/**
	 * 셀 - SKU 매핑 시점 (P: 주문 가공시, A: 분류 시)
	 * 
	 * @param batch
	 * @return
	 */
	public static String getCellSkuMappingPoint(JobBatch batch) {
		// job.rtn.cell-sku.mapping.point						
		return getConfigValue(batch, DasConfigConstants.RTN_CELL_SKU_MAPPING_POINT, "P");
	}
	
	/**
	 * 셀에 할당할 대상 필드 (매장, 상품, 주문번호 …)
	 * 
	 * @param batch
	 * @return
	 */
	public static String getBoxMappingTargetField(JobBatch batch) {
		// job.rtn.preproces.cell.mapping.field
		return getConfigValue(batch, DasConfigConstants.RTN_PREPROCESS_CELL_MAPPING_FIELD, true);
	}
	
}
