package operato.logis.das.service.impl;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.event.rest.DeviceProcessRestEvent;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 반품 장비로 부터의 요청을 처리하는 서비스 
 * 
 * @author shortstop
 */
@Component("rtnDeviceProcessService")
public class RtnDeviceProcessService extends AbstractExecutionService{
	
	/**
	 * 슈트 정보를 받아서 유효한 지 체크한 후 호기/슈트 정보를 리턴
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/chute_info','RTN')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public Object validateChute(DeviceProcessRestEvent event) {
		
		String chuteNo = event.getRequestParams().get("chuteNo").toString();
		Query query = AnyOrmUtil.newConditionForExecution(event.getDomainId());
		
		query.addFilter("chuteNo", chuteNo);
		Rack rack = this.queryManager.selectByCondition(Rack.class, query);

		if(rack == null) {
			// 슈트 번호(1) 을(를) 찾을수 없습니다
			throw ThrowUtil.newNotFoundRecord("terms.label.chute_no", chuteNo);
		}
		
		Map<String, Object> retValue = ValueUtil.newMap("rack_cd,rack_nm,chute_no", rack.getRackCd(), rack.getRackNm(), chuteNo);
		event.setResult(retValue);
		event.setExecuted(true);
		return retValue;
	}

}
