package operato.logis.das.service.api;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.service.api.IIndicationService;

/**
 * DAS 표시기 서비스 인터페이스
 * 
 * @author shortstop
 */
public interface IDasIndicationService extends IIndicationService {
	
	/**
	 * 작업 배치에 상품이 할당된 모든 셀에 박스 매핑 표시 
	 * 
	 * @param batch
	 */
	public void displayAllForBoxMapping(JobBatch batch);
}
