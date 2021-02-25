package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Printer;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.classfy.ClassifyInEvent;
import xyz.anythings.base.event.rest.DeviceProcessRestEvent;
import xyz.anythings.base.model.BatchProgressRate;
import xyz.anythings.base.model.EquipBatchSet;
import xyz.anythings.base.rest.PrinterController;
import xyz.anythings.base.service.api.IIndicationService;
import xyz.anythings.base.service.impl.LogisServiceDispatcher;
import xyz.anythings.base.service.util.LogisServiceUtil;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.sys.event.model.ErrorEvent;
import xyz.anythings.sys.event.model.PrintEvent;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.model.BaseResponse;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.service.ICustomService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.elidom.sys.entity.Domain;
import xyz.elidom.sys.rest.DomainController;
import xyz.elidom.sys.system.context.DomainContext;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.sys.util.ValueUtil;

/**
 * 출고용 장비로 부터의 요청을 처리하는 서비스 
 * 
 * @author shortstop
 */
@Component("dasDeviceProcessService")
public class DasDeviceProcessService extends AbstractExecutionService {
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
		
		// 1. 작업 배치 조회
		Long domainId = event.getDomainId();
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
	 * 											표시기 점/소등 A P I
	 *****************************************************************************************************
	/**
	 * 완료 상태 표시기 END 표시 복원
	 * 
	 * @param event
	 */
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/indicators/on/end_cells', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void restoreEndList(DeviceProcessRestEvent event) {
		// 1. 작업 배치 조회
		Map<String, Object> reqParams = event.getRequestParams();
		String equipType = reqParams.get("equipType").toString();
		String equipCd = reqParams.get("equipCd").toString();
		
		// 2. 현재 작업 배치 조회
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
		
		// 3. 배치 소속 게이트웨이 조회
		List<Gateway> gwList = indSvc.searchGateways(batch);
		String sql = this.dasQueryStore.getRestoreEndIndicators();
		
		// 4. 작업 배치, 게이트웨이에 걸린 셀 중에 ENDING, ENDED 상태인 셀을 모두 조회
		for(Gateway gw : gwList) {
			Map<String, Object> condition = 
				ValueUtil.newMap("domainId,batchId,stageCd,jobType,equipCd,gwPath,gwCd,indStatuses", domainId, batch.getId(), batch.getStageCd(), batch.getJobType(), equipCd, gw.getGwNm(), gw.getGwCd(), LogisConstants.CELL_JOB_STATUS_END_LIST);
			List<JobInstance> jobList = this.queryManager.selectListBySql(sql, condition, JobInstance.class, 0, 0);
		
			// 5. ENDING, ENDED 조회한 정보로 모두 점등
			if(ValueUtil.isNotEmpty(jobList)) {
				for(JobInstance job : jobList) {
					indSvc.indicatorOnForPickEnd(job, ValueUtil.isEqualIgnoreCase(job.getStatus(), LogisConstants.CELL_JOB_STATUS_ENDED));
				}
			}
		}
	}
	
	/*****************************************************************************************************
	 * 											D A S 투 입 A P I
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
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 이벤트 처리 결과 셋팅
		Map<String, Object> condition = ValueUtil.newMap("comCd,skuCd", comCd, skuCd);
		List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchJobList(batch, condition);
		
		// 4. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, jobList));
		event.setExecuted(true);
	}
	
	/**
	 * DAS 마지막 상품 투입 취소
	 *
	 * @param event
	 * @return
	 */ 
	@SuppressWarnings("unchecked")
	@EventListener(classes=DeviceProcessRestEvent.class, condition = "#event.checkCondition('/cancel/input/sku', 'das')")
	@Order(Ordered.LOWEST_PRECEDENCE)
	public void cancelInputSku(DeviceProcessRestEvent event) {
		// 1. 파라미터
		Map<String, Object> reqParams = event.getRequestParams();
		String equipType = reqParams.get("equipType").toString();
		String equipCd = reqParams.get("equipCd").toString();
		
		// 2. 작업 배치
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, equipType, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 3. 마지막 투입 상품 조회
		Integer lastInputSeq = batch.getLastInputSeq();
		if(lastInputSeq == null || lastInputSeq < 1) {
			throw ThrowUtil.newValidationErrorWithNoLog("투입된 상품이 없습니다.");
		}
		
		// 4. 작업 현황 조회
		Map<String, Object> condition = ValueUtil.newMap("inputSeq", batch.getLastInputSeq());
		List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchPickingJobList(batch, condition);
		
		// 5. 소등 대상 게이트웨이 & 표시기 리스트 추출
		Map<String, Object> gwIndMap = new HashMap<String, Object>();
		for(JobInstance job : jobList) {
			if(job.isDoneJob()) {
				throw ThrowUtil.newValidationErrorWithNoLog("분류 처리된 상품이 있어서 투입 취소할 수 없습니다. 개별 취소로 처리하세요.");
			} else {
				if(ValueUtil.isEqualIgnoreCase(job.getStatus(), LogisConstants.JOB_STATUS_PICKING)) {
					String gwPath = job.getGwPath();
					boolean isContainsGw = gwIndMap.containsKey(gwPath);
					List<String> indList = isContainsGw ? (List<String>)gwIndMap.get(gwPath) : new ArrayList<String>();
					if(!isContainsGw) gwIndMap.put(gwPath, indList);
					indList.add(job.getIndCd());
				}
			}
		}
		
		// 6. 투입 삭제 처리
		condition = ValueUtil.newMap("domainId,batchId,inputSeq", domainId, batch.getId(), batch.getLastInputSeq());
		JobInput jobInput = this.queryManager.selectByCondition(JobInput.class, condition);
		if(jobInput == null) {
			throw ThrowUtil.newValidationErrorWithNoLog("마지막 투입 시퀀스의 투입 정보가 없습니다.");
		} else {
			this.queryManager.delete(jobInput);
		}
		
		// 7. 작업 현황 업데이트
		String sql = "update job_instances set status = :status, picking_qty = 0, input_at = null, pick_started_at = null, input_seq = 0, ind_cd = null, color_cd = null where domain_id = :domainId and batch_id = :batchId and input_seq = :inputSeq and com_cd = :comCd and sku_cd = :skuCd";
		condition.put("status", LogisConstants.JOB_STATUS_WAIT);
		condition.put("comCd", jobInput.getComCd());
		condition.put("skuCd", jobInput.getSkuCd());
		this.queryManager.executeBySql(sql, condition);
		
		// 8. 배치 마지막 시퀀스 업데이트
		batch.setLastInputSeq(batch.getLastInputSeq() - 1);
		this.queryManager.update(batch, "lastInputSeq");
		
		// 9. 표시기 소등
		IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
		if(ValueUtil.isNotEmpty(gwIndMap)) {
			Iterator<String> gwIter = gwIndMap.keySet().iterator();
			while(gwIter.hasNext()) {
				String gwPath = gwIter.next();
				List<String> indList = (List<String>)gwIndMap.get(gwPath);
				indSvc.indicatorListOff(domainId, batch.getStageCd(), gwPath, indList);
			}
		}
		
		// 10. 이벤트 처리 결과 셋팅
		event.setReturnResult(new BaseResponse(true, LogisConstants.OK_STRING, null));
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
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, equipType, equipCd);
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
		
		// 2. 작업 배치 조회
		Long domainId = event.getDomainId();
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
		
		// 1. 작업 배치 조회
		Long domainId = event.getDomainId();
		EquipBatchSet equipBatchSet = LogisServiceUtil.checkRunningBatch(domainId, LogisConstants.EQUIP_TYPE_RACK, equipCd);
		JobBatch batch = equipBatchSet.getBatch();
		
		// 2. 박스 정보 조회
		BoxPack box = AnyEntityUtil.findEntityBy(domainId, false, BoxPack.class, "*", "boxId", boxId);
		
		if(box == null) {
			box = AnyEntityUtil.findEntityBy(domainId, false, BoxPack.class, "*", "invoiceId", invoiceId);
		}
		
		// 3. 프린터 ID 조회
		if(ValueUtil.isEmpty(printerId)) {
			printerId = AnyEntityUtil.findItemOneColumn(domainId, true, String.class, Cell.class, "printer_cd", "cellCd", cellCd);
		}
		
		// 4. 프린트 이벤트 전송
		String labelTemplate = DasBatchJobConfigUtil.getInvoiceLabelTemplate(batch);
		Map<String, Object> printParams = ValueUtil.newMap("box", box);
		PrintEvent printEvent = new PrintEvent(domainId, batch.getJobType(), printerId, labelTemplate, printParams);
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
