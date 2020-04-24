package operato.logis.das.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.classfy.ClassifyInEvent;
import xyz.anythings.base.event.rest.DeviceProcessRestEvent;
import xyz.anythings.base.model.EquipBatchSet;
import xyz.anythings.base.service.impl.LogisServiceDispatcher;
import xyz.anythings.base.service.util.LogisServiceUtil;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.elidom.sys.entity.Domain;

/**
 * 출고용 장비로 부터의 요청을 처리하는 서비스 
 * 
 * @author shortstop
 */
@Component("dasDeviceProcessService")
public class DasDeviceProcessService extends AbstractExecutionService {

	@Autowired
	private LogisServiceDispatcher serviceDispatcher;
	
	/**
	 * DAS 표시기를 이용한 검수 처리
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspect_by_indicator', 'DAS')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public Object inspectByIndicator(DeviceProcessRestEvent event) {
		String equipCd = event.getRequestParams().get("equipCd").toString();
		//String stationCd = event.getRequestParams().get("stationCd").toString();
		//String comCd = event.getRequestParams().get("comCd").toString();
		String skuCd = event.getRequestParams().get("skuCd").toString();
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(Domain.currentDomainId(), LogisConstants.EQUIP_TYPE_RACK, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		IClassifyInEvent inspectionEvent = new ClassifyInEvent(batch, SysEvent.EVENT_STEP_ALONE, true, LogisCodeConstants.CLASSIFICATION_INPUT_TYPE_SKU, skuCd, 0);
		return this.serviceDispatcher.getAssortService(batch).inputForInspection(inspectionEvent);
	}

}

