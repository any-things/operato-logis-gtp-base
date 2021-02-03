package operato.logis.das.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import operato.logis.das.query.store.DasQueryStore;
import operato.logis.das.service.util.DasBatchJobConfigUtil;
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Printer;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.classfy.ClassifyInEvent;
import xyz.anythings.base.event.rest.DeviceProcessRestEvent;
import xyz.anythings.base.model.BatchProgressRate;
import xyz.anythings.base.model.EquipBatchSet;
import xyz.anythings.base.rest.PrinterController;
import xyz.anythings.base.service.impl.LogisServiceDispatcher;
import xyz.anythings.base.service.util.LogisServiceUtil;
import xyz.anythings.sys.event.model.ErrorEvent;
import xyz.anythings.sys.event.model.PrintEvent;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.model.BaseResponse;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.service.ICustomService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.sys.entity.User;
import xyz.elidom.sys.rest.DomainController;
import xyz.elidom.sys.system.context.DomainContext;
import xyz.elidom.sys.util.ValueUtil;
import xyz.elidom.util.DateUtil;

/**
 * 출고용 장비로 부터의 요청을 처리하는 서비스 
 * 
 * @author shortstop
 */
@Component("dasDeviceProcessService")
public class DasDeviceProcessService extends AbstractExecutionService {
	/**
	 * 커스텀 서비스 - 출고 검수 후 처리
	 */
	private static final String DIY_DAS_AFTER_INSPECTION = "diy-das-after-inspection";
	/**
	 * 커스텀 서비스
	 */
	@Autowired
	protected ICustomService customService;
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
	 * DAS Query Store
	 */
	@Autowired
	private DasQueryStore dasQueryStore;
	
	/*****************************************************************************************************
	 * 									 DAS 작업 진행율 A P I
	 *****************************************************************************************************
	
	/**
	 * 배치 그룹의 작업 진행 요약 정보 조회
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/total_progress_rate', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void totalProgressRate(DeviceProcessRestEvent event) {
		Map<String, Object> reqParams = event.getRequestParams();
		String equipType = reqParams.get("equipType").toString();
		String equipCd = reqParams.get("equipCd").toString();
		boolean includeTotal = reqParams.containsKey("includeTotal") ? ValueUtil.isEqualIgnoreCase(reqParams.get("includeTotal").toString(), LogisConstants.TRUE_STRING) : false;
		boolean includeRack = reqParams.containsKey("includeRack") ? ValueUtil.isEqualIgnoreCase(reqParams.get("includeRack").toString(), LogisConstants.TRUE_STRING) : false;
		
		Long domainId = event.getDomainId();
		// 1. 작업 배치 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, equipType, equipCd);
		// 2. 호기내 작업 배치 ID로 작업 배치 조회
		JobBatch batch = equipBatchSet.getBatch();
		BatchProgressRate totalRate = null;
		BatchProgressRate rackRate = null;
		
		// 3. 작업 진행율 정보 조회
		if(includeTotal) {
			Map<String, Object> queryParams = ValueUtil.newMap("domainId,batchGroupId", domainId, batch.getBatchGroupId());
			String sql = this.dasQueryStore.getTotalBatchProgressRateQuery();
			totalRate = this.queryManager.selectBySql(sql, queryParams, BatchProgressRate.class);
		}
		
		if(includeRack) {
			rackRate = this.serviceDispatcher.getJobStatusService(batch).getBatchProgressSummary(batch);
		}
		
		// 4. 리턴 결과 설정
		event.setExecuted(true);
		if(includeTotal && includeRack) {
			Map<String, Object> totalResult = ValueUtil.newMap("total,rack", totalRate, rackRate);
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, totalResult));
		} else if(!includeTotal && !includeRack) {
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING));
		} else {
			event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, (totalRate == null ? rackRate : totalRate)));
		}
	}

	/*****************************************************************************************************
	 * 										DAS 투입 리스트 화면 A P I
	 *****************************************************************************************************
	/**
	 * DAS 투입 순번 작업 리스트 조회
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/search/input_jobs', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void searchInputJobItems(DeviceProcessRestEvent event) {
		// 1. 파라미터
		Map<String, Object> reqParams = event.getRequestParams();
		String equipType = reqParams.get("equipType").toString();
		String equipCd = reqParams.get("equipCd").toString();
		String comCd = reqParams.get("comCd").toString();
		String skuCd = reqParams.get("skuCd").toString();
		
		// 2. 작업 배치
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(Domain.currentDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 이벤트 처리 결과 셋팅
		Map<String, Object> condition = ValueUtil.newMap("comCd,skuCd", comCd, skuCd);
		List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchJobList(batch, condition);
		
		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, jobList));
		event.setExecuted(true);
	}
	
	/*****************************************************************************************************
	 * 										DAS 표시기 검수 A P I
	 *****************************************************************************************************
	/**
	 * DAS 표시기를 이용한 검수 처리
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspect_by_indicator', 'DAS')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void inspectByIndicator(DeviceProcessRestEvent event) {
		// 1. 파라미터
		Map<String, Object> reqParams = event.getRequestParams();
		String equipType = reqParams.get("equipType").toString();
		String equipCd = reqParams.get("equipCd").toString();
		String comCd = reqParams.get("comCd").toString();
		String skuCd = reqParams.get("skuCd").toString();
		
		// 2. 작업 배치 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(Domain.currentDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 이벤트 처리 결과 셋팅
		IClassifyInEvent inspectionEvent = new ClassifyInEvent(batch, SysEvent.EVENT_STEP_ALONE, true, LogisCodeConstants.CLASSIFICATION_INPUT_TYPE_SKU, skuCd, 0);
		inspectionEvent.setComCd(comCd);
		Object result = this.serviceDispatcher.getAssortService(batch).inputForInspection(inspectionEvent);
		
		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, result));
		event.setExecuted(true);
	}
	
	/*****************************************************************************************************
	 *											출 고 검 수 A P I
	 *****************************************************************************************************
	
	/**
	 * 출고 검수를 위한 검수 정보 조회 - 박스 ID
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/find_by_box', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void findByBox(DeviceProcessRestEvent event) {
		// 1. 파라미터
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String boxId = params.get("boxId").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();

		// 3. 검수 정보 조회
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("boxId", boxId);
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
		if(boxPack != null) {
			boxPack.searchBoxItems();
		}

		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, boxPack));
		event.setExecuted(true);
	}
	
	/**
	 * 출고 검수를 위한 검수 정보 조회 - 송장 번호
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/find_by_invoice', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void findByInvoice(DeviceProcessRestEvent event) {
		// 1. 파라미터
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String invoiceId = params.get("invoiceId").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.findBatchByEquip(event.getDomainId(), equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 검수 정보 조회
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("invoiceId", invoiceId);
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
		if(boxPack != null) {
			boxPack.searchBoxItems();
		}
		
		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, boxPack));
		event.setExecuted(true);
	}
	
	/**
	 * 출고 검수 완료
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/inspection/finish', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void finishInspection(DeviceProcessRestEvent event) {
		// 1. 파라미터 
		Map<String, Object> params = event.getRequestParams();
		String equipCd = params.get("equipCd").toString();
		String equipType = params.get("equipType").toString();
		String classCd = params.get("classCd").toString();
		String boxId = params.get("boxId").toString();
		String printerId = params.get("printerId").toString();
		
		// 2. 설비 코드로 현재 진행 중인 작업 배치 및 설비 정보 조회
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = LogisServiceUtil.findBatchByEquip(domainId, equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 박스 조회
		BoxPack box = AnyEntityUtil.findEntityBy(event.getDomainId(), false, BoxPack.class, null, "batchId,boxId", batch.getId(), boxId);
		box.setStatus(LogisConstants.JOB_STATUS_EXAMINATED);
		box.setManualInspStatus(LogisConstants.PASS_STATUS);
		box.setManualInspectedAt(DateUtil.currentTimeStr());
		
		// 4. 작업 정보 검수 완료 처리
		Map<String, Object> updateParams = ValueUtil.newMap("status,manualInspStatus,nowStr,now,updaterId,domainId,batchId,classCd,boxId",
				LogisConstants.JOB_STATUS_EXAMINATED,
				LogisConstants.PASS_STATUS,
				DateUtil.currentTimeStr(),
				new Date(),
				User.currentUser().getId(),
				domainId,
				batch.getId(),
				classCd,
				boxId
			);
		
		String sql = "update job_instances set inspected_qty = pick_qty, status = :status, manual_insp_status = :manualInspStatus, manual_inspected_at = :nowStr, updater_id = :updaterId, updated_at = :now where domain_id = :domainId and batch_id = :batchId and class_cd = :classCd and box_id = :boxId";
		this.queryManager.executeBySql(sql, updateParams);
		
		// 5. 커스텀 서비스 호출 (ex: 송장 인쇄, 박스 실적 전송 등 처리)
		this.customService.doCustomService(domainId, DIY_DAS_AFTER_INSPECTION, ValueUtil.newMap("batch,box,printerId", batch, box, printerId));

		// 6. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, box));
		event.setExecuted(true);
	}
	
	/*****************************************************************************************************
	 * 											D A S 송 장 인 쇄 A P I
	 *****************************************************************************************************
	
	/**
	 * 배치내 랙의 모든 셀에 남은 상품으로 일괄 풀 박스 처리
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/batch_boxing', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void fullboxingAllRemained(DeviceProcessRestEvent event) {
		// 1. 파라미터 처리
		Map<String, Object> reqParams = event.getRequestParams();
		String equipCd = ValueUtil.toString(reqParams.get("equipCd"));
		Long domainId = Domain.currentDomainId();
		
		// 2. 작업 배치 조회
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, LogisConstants.EQUIP_TYPE_RACK, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		this.serviceDispatcher.getBoxingService(batch).batchBoxing(batch);
		
		// 3. 결과 리턴
		event.setExecuted(true);
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING));
	}
	
	/**
	 * 송장 출력
	 *
	 * @param event
	 * @return
	 */ 
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/print_invoice', 'DAS')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void printInvoiceLabel(DeviceProcessRestEvent event) {
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
		PrintEvent printEvent = new PrintEvent(batch.getDomainId(), batch.getJobType(), printerId, labelTemplate, printParams);
		this.eventPublisher.publishEvent(printEvent);
		
		// 5. 이벤트 결과 처리
		event.setExecuted(true);
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING));
	}

	/*****************************************************************************************************
	 * 											이 벤 트 처 리 A P I
	 *****************************************************************************************************
	/**
	 * 송장 라벨 인쇄 API
	 * 
	 * @param printEvent
	 */
	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, classes = PrintEvent.class, condition = "#printEvent.jobType == 'DAS'")
	public void printLabel(PrintEvent printEvent) {
		
		// 현재 도메인 조회
		Long domainId = printEvent.getDomainId();
		Domain domain = this.domainCtrl.findOne(domainId, null);
		// 현재 도메인 설정
		DomainContext.setCurrentDomain(domain);
		
		try {
			// 인쇄 옵션 정보 추출
			Printer printer = this.queryManager.select(Printer.class, printEvent.getPrinterId());
			printer = (printer == null) ? this.queryManager.selectByCondition(Printer.class, ValueUtil.newMap("domainId,printerCd", domainId, printEvent.getPrinterId())) : printer;
			String agentUrl = printer.getPrinterAgentUrl();
			String printerName = printer.getPrinterDriver();
			
			// 인쇄 요청
			this.printerCtrl.printLabelByLabelTemplate(agentUrl, printerName, printEvent.getPrintTemplate(), printEvent.getTemplateParams());
			printEvent.setExecuted(true);
			
		} catch (Exception e) {
			// 예외 처리
			ErrorEvent errorEvent = new ErrorEvent(domain.getId(), "DAS_PRINT_LABEL_ERROR", e, null, true, true);
			this.eventPublisher.publishEvent(errorEvent);
			
		} finally {
			// 스레드 로컬 변수에서 currentDomain 리셋 
			DomainContext.unsetAll();
		}
	}

}
