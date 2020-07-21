package operato.logis.das.service.impl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import operato.logis.das.service.util.DasBatchJobConfigUtil;
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Printer;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.classfy.ClassifyInEvent;
import xyz.anythings.base.event.rest.DeviceProcessRestEvent;
import xyz.anythings.base.model.EquipBatchSet;
import xyz.anythings.base.rest.PrinterController;
import xyz.anythings.base.service.impl.LogisServiceDispatcher;
import xyz.anythings.base.service.util.LogisServiceUtil;
import xyz.anythings.sys.event.model.ErrorEvent;
import xyz.anythings.sys.event.model.PrintEvent;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.sys.rest.DomainController;
import xyz.elidom.sys.system.context.DomainContext;
import xyz.elidom.sys.util.ValueUtil;

/**
 * 출고용 장비로 부터의 요청을 처리하는 서비스 
 * 
 * @author shortstop
 */
@Component("dasDeviceProcessService")
public class DasDeviceProcessService extends AbstractExecutionService {
	
	/**
	 * 도메인 컨트롤러
	 */
	@Autowired
	private DomainController domainCtrl;
	/**
	 * 프린터 컨트롤러
	 */
	@Autowired
	private PrinterController printerCtrl;
	/**
	 * 서비스 디스패쳐
	 */
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
	
	/**
	 * 호기의 모든 로케이션의 마지막 송장 출력 
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/print_invoice/last_all', 'DAS')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public Object printLastAllInvoiceLabel(DeviceProcessRestEvent event) {
		Map<String, Object> reqParams = event.getRequestParams();
		String equipCd = ValueUtil.toString(reqParams.get("equipCd"));
		Long domainId = Domain.currentDomainId();
		
		// 1. 작업 배치 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, LogisConstants.EQUIP_TYPE_RACK, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 2. TODO 해당 호기의 모든 셀의 마지막 박스 정보 조회
		
		// 3. 결과 리턴 
		return batch;
	}
	
	/**
	 * 송장 출력 
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/print_invoice', 'DAS')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public Object printInvoiceLabel(DeviceProcessRestEvent event) {
		Map<String, Object> reqParams = event.getRequestParams();
		String equipCd = ValueUtil.toString(reqParams.get("equipCd"));
		String boxId = ValueUtil.toString(reqParams.get("boxId"));
		String invoiceId = ValueUtil.toString(reqParams.get("invoiceId"));
		String cellCd = ValueUtil.toString(reqParams.get("cellCd"));
		String printerId = ValueUtil.toString(reqParams.get("printerId"));
		Long domainId = Domain.currentDomainId();
		
		// 1. 작업 배치 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, LogisConstants.EQUIP_TYPE_RACK, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 2. 박스 정보 조회
		BoxPack box = AnyEntityUtil.findEntityBy(domainId, false, BoxPack.class, "domainId,boxId", domainId, boxId);
		
		if(box == null) {
			box = AnyEntityUtil.findEntityBy(domainId, false, BoxPack.class, "domainId,invoiceId", domainId, invoiceId);
		}
		
		// 3. 프린터 ID 조회
		if(ValueUtil.isEmpty(printerId)) {
			printerId = AnyEntityUtil.findItemOneColumn(domainId, true, String.class, Cell.class, "printer_cd", "domainId,cellCd", domainId, cellCd);
		}
		
		// 4. 프린트 이벤트 전송
		String labelTemplate = DasBatchJobConfigUtil.getInvoiceLabelTemplate(batch);
		Map<String, Object> printParams = ValueUtil.newMap("box", box);
		PrintEvent printEvent = new PrintEvent(batch.getDomainId(), printerId, labelTemplate, printParams);
		this.eventPublisher.publishEvent(printEvent);
		
		// 5. 결과 리턴 
		return printEvent;
	}

	/**
	 * 송장 라벨 인쇄 API
	 * 
	 * @param printEvent
	 */
	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = PrintEvent.class)
	public void printLabel(PrintEvent printEvent) {
		
		// 현재 도메인 조회
		Domain domain = this.domainCtrl.findOne(printEvent.getDomainId(), null);
		// 현재 도메인 설정
		DomainContext.setCurrentDomain(domain);
		
		try {
			// 인쇄 옵션 정보 추출
			Printer printer = this.queryManager.select(Printer.class, printEvent.getPrinterId());
			String agentUrl = printer.getPrinterAgentUrl();
			String printerName = printer.getPrinterDriver();
			
			// 인쇄 요청
			this.printerCtrl.printLabelByLabelTemplate(agentUrl, printerName, printEvent.getPrintTemplate(), printEvent.getTemplateParams());
			
		} catch (Exception e) {
			// 예외 처리
			ErrorEvent errorEvent = new ErrorEvent(domain.getId(), "PRINT_LABEL_ERROR", e, null, true, true);
			this.eventPublisher.publishEvent(errorEvent);
			
		} finally {
			// 스레드 로컬 변수에서 currentDomain 리셋 
			DomainContext.unsetAll();
		}
	}

}

