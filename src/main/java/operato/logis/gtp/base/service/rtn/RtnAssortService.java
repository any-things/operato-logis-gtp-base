package operato.logis.gtp.base.service.rtn;
  
import java.util.Map; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
 
import xyz.anythings.base.entity.Rack; 
import xyz.anythings.base.event.rest.DeviceProcessRestEvent; 
import xyz.anythings.base.service.impl.SkuSearchService;
import xyz.anythings.sys.service.AbstractExecutionService; 
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;   
import xyz.elidom.sys.entity.Domain; 
import xyz.elidom.sys.util.ThrowUtil;  
import xyz.elidom.util.ValueUtil; 



/**
 * 반품 분류 서비스  구현
 *
 * @author shortstop
 */
@Component("rtnAssortService")
public class RtnAssortService extends AbstractExecutionService {
	/**
	 * 상품 코드로 상품 조회 서비스
	 */
	@Autowired
	protected SkuSearchService skuSearchService;
	 
	/**
	 * 슈트 정보를 받아서 유효한 지 체크한 후 호기/슈트 정보를 리턴
	 *
	 * @param chuteNo
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/chute_info','RTN')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public Object validateChute(DeviceProcessRestEvent event) {
		Long domainId = Domain.currentDomainId();
		String chuteNo = event.getRequestParams().get("chuteNo").toString();
		Query query = AnyOrmUtil.newConditionForExecution(domainId);
		query.addFilter("chuteNo", chuteNo);
		Rack rack = this.queryManager.selectByCondition(Rack.class, query);

		if(rack == null) {
			// 슈트 번호(1) 을(를) 찾을수 없습니다
			throw ThrowUtil.newNotFoundRecord("terms.label.chute_no", chuteNo);
		}
		Map<String, Object> retValue= ValueUtil.newMap("rack_cd,rack_nm,chute_no", rack.getRackCd(), rack.getRackNm(), chuteNo);
		
		event.setResult(retValue);
		event.setExecuted(true); 
		
		return retValue;
	}

}
