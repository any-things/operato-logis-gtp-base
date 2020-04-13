package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.DasQueryStore;
import operato.logis.das.service.api.IDasIndicationService;
import operato.logis.das.service.util.DasBatchJobConfigUtil;
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.SKU;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.ICategorizeEvent;
import xyz.anythings.base.event.IClassifyErrorEvent;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.IClassifyOutEvent;
import xyz.anythings.base.event.IClassifyRunEvent;
import xyz.anythings.base.event.classfy.ClassifyErrorEvent;
import xyz.anythings.base.event.classfy.ClassifyRunEvent;
import xyz.anythings.base.event.input.InputEvent;
import xyz.anythings.base.model.Category;
import xyz.anythings.base.model.CategoryItem;
import xyz.anythings.base.service.api.IAssortService;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.api.IIndicationService;
import xyz.anythings.base.service.impl.AbstractClassificationService;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.gw.entity.Indicator;
import xyz.anythings.gw.service.mq.model.device.DeviceCommand;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.ElidomException;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ThreadUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 출고용 분류 서비스 구현
 *
 * @author shortstop
 */
@Component("dasAssortService")
public class DasAssortService extends AbstractClassificationService implements IAssortService {

	/**
	 * 박스 서비스
	 */
	@Autowired
	private DasBoxingService boxService;
	/**
	 * DAS 쿼리 스토어
	 */
	@Autowired
	private DasQueryStore dasQueryStore;
	
	@Override
	public String getJobType() {
		return LogisConstants.JOB_TYPE_DAS;
	}

	@Override
	public IBoxingService getBoxingService(Object... params) {
		return this.boxService;
	}
	
	@Override
	public Object boxCellMapping(JobBatch batch, String cellCd, String boxId) {
		return this.boxService.assignBoxToCell(batch, cellCd, boxId);
	}
	
	@Override
	public void batchStartAction(JobBatch batch) {
		// 설정에서 작업배치 시에 게이트웨이 리부팅 할 지 여부 조회
		boolean gwReboot = DasBatchJobConfigUtil.isGwRebootWhenInstruction(batch);
		
		if(gwReboot) {
			IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
			List<Gateway> gwList = indSvc.searchGateways(batch);
			
			// 게이트웨이 리부팅 처리
			for(Gateway gw : gwList) {
				indSvc.rebootGateway(batch, gw);
			}
		}
		
		// 설정에서 작업 지시 시점에 박스 매핑 표시 여부 조회 		
		if(DasBatchJobConfigUtil.isIndOnAssignedCellWhenInstruction(batch)) {
			// 게이트웨이 리부팅 시에는 리부팅 프로세스 완료시까지 약 1분여간 기다린다.
			if(gwReboot) {
				// TODO 아래 시간도 작업 설정으로 ...
				ThreadUtil.sleep(60000);
			}
			
			// 표시기에 박스 매핑 표시 
			((IDasIndicationService)this.serviceDispatcher.getIndicationService(batch)).displayAllForBoxMapping(batch);
		}
	}

	@Override
	public void batchCloseAction(JobBatch batch) {
		// 모든 셀에 남아 있는 잔량에 대해 풀 박싱 여부 조회 		
		if(DasBatchJobConfigUtil.isBatchFullboxWhenClosingEnabled(batch)) {
			// 배치 풀 박싱
			this.boxService.batchBoxing(batch);
		}
	}

	@Override
	public Category categorize(ICategorizeEvent event) {
		// 1. 배치 추출
		Long domainId = event.getDomainId();
		JobBatch batch = event.getJobBatch();
		
		// 2. 상품 체크를 위한 조회
		SKU sku = AnyEntityUtil.findEntityBy(domainId, true, SKU.class, null, "domainId,comCd,skuCd", domainId, batch.getComCd(), event.getInputCode());
		
		// 3. 중분류 정보 조회
		String batchGroupId = event.getBatchGroupId();
		Map<String, Object> params = ValueUtil.newMap("domainId,comCd,skuCd,jobType", domainId, sku.getComCd(), sku.getSkuCd(), batch.getJobType());
		params.put(ValueUtil.isEmpty(batchGroupId) ? "batchStatus" : "batchGroupId", ValueUtil.isEmpty(batchGroupId) ? JobBatch.STATUS_RUNNING : batchGroupId);
		
		// 4. 중분류 수량을 분류 처리한 수량에서 제외할 건지 여부 설정		
		boolean fixedQtyMode = DasBatchJobConfigUtil.isCategorizationDisplayFixedQtyMode(batch);		
		//    호기를 좌측 정렬할 지 우측 정렬할 지 여부 설정
		boolean regSortAsc = DasBatchJobConfigUtil.isCategorizationRackSortMode(batch);
		params.put(fixedQtyMode ? "qtyFix" : "qtyFilter", true);
		params.put(regSortAsc ? "rackAsc" : "rackDesc", true);
		
		// 5. 중분류 쿼리 조회
		String sql = this.dasQueryStore.getDasCategorizationQuery();
		List<CategoryItem> items = this.queryManager.selectListBySql(sql, params, CategoryItem.class, 0, 0);
		
		// 6. 최종 호기 순 (혹은 역순)으로 중분류 정보 재배치
		return new Category(batchGroupId, sku, items);
	}

	@Override
	public String checkInput(JobBatch batch, String inputId, Object... params) {
		// inputId를 체크하여 어떤 코드 인지 (상품 코드, 상품 바코드, 박스 ID, 랙 코드, 셀 코드 등)를 찾아서 리턴 
		if(BatchJobConfigUtil.isBoxIdValid(batch, inputId)) {
			return LogisCodeConstants.INPUT_TYPE_BOX_ID;
			
		} else if(BatchJobConfigUtil.isSkuCdValid(batch, inputId)) {
			return LogisCodeConstants.INPUT_TYPE_SKU_CD;
		
		} else if(BatchJobConfigUtil.isRackCdValid(batch, inputId)) {
			return LogisCodeConstants.INPUT_TYPE_RACK_CD;
			
		} else if(BatchJobConfigUtil.isCellCdValid(batch, inputId)) {
			return LogisCodeConstants.INPUT_TYPE_CELL_CD;
			
		} else if(BatchJobConfigUtil.isIndCdValid(batch, inputId)) {
			return LogisCodeConstants.INPUT_TYPE_IND_CD;
			
		} else {
			// 스캔한 정보가 어떤 투입 유형인지 구분할 수 없습니다.
			String msg = MessageUtil.getMessage("CANT_DISTINGUISH_WHAT_INPUT_TYPE", "Can't distinguish what type of input the scanned information is.");
			throw new ElidomRuntimeException(msg);
		}
	}

	@Override
	public Object input(IClassifyInEvent inputEvent) { 
		return this.inputSkuSingle(inputEvent); 
		
	} 
	
	@EventListener(classes = ClassifyRunEvent.class, condition = "#exeEvent.jobType == 'DAS'")
	public Object classify(IClassifyRunEvent exeEvent) { 
		String classifyAction = exeEvent.getClassifyAction();
		JobInstance job = exeEvent.getJobInstance();
		
		try {
			switch(classifyAction) {
				// 확정 처리
				case LogisCodeConstants.CLASSIFICATION_ACTION_CONFIRM :
					this.confirmAssort(exeEvent);
					break;
					
				// 작업 취소
				case LogisCodeConstants.CLASSIFICATION_ACTION_CANCEL :
					this.cancelAssort(exeEvent);
					break;
					
				// 수량 조정 처리  
				case LogisCodeConstants.CLASSIFICATION_ACTION_MODIFY :
					this.splitAssort(exeEvent);
					break;
					
				// 풀 박스
				case LogisCodeConstants.CLASSIFICATION_ACTION_FULL :
					if(exeEvent instanceof IClassifyOutEvent) {
						this.fullBoxing((IClassifyOutEvent)exeEvent);
					}
					break;
			}
		} catch (Throwable th) {
			IClassifyErrorEvent errorEvent = new ClassifyErrorEvent(exeEvent, exeEvent.getEventStep(), th);
			this.handleClassifyException(errorEvent);
			return exeEvent;
		}
		
		exeEvent.setExecuted(true);
		return job;
	}
	 
	@Override
	public Object output(IClassifyOutEvent outputEvent) {
		return this.fullBoxing(outputEvent);
	}

	@Override
	public Object inputSkuSingle(IClassifyInEvent inputEvent) {
		// 1. 이벤트에서 데이터 추출
		Long domainId = inputEvent.getDomainId();
		JobBatch batch = inputEvent.getJobBatch(); 
		String comCd = inputEvent.getComCd();
		String skuCd = inputEvent.getInputCode();
		
		// 2. 투입 상품이 현재 작업 배치에 존재하는 상품인지 체크
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("comCd", comCd);
		condition.addFilter("skuCd", SysConstants.EQUAL, skuCd);
		condition.addFilter("equipCd", batch.getEquipCd());
		
		if(this.queryManager.selectSize(JobInstance.class, condition) == 0) {
			// 스캔한 상품은 현재 작업 배치에 존재하지 않습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_SKU_FOUND_IN_SCOPE", "terms.label.job_batch");
		}		
				
		// 3. 작업 인스턴스 조회
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,equipCd,comCd,skuCd", domainId, batch.getId(), batch.getEquipCd(), comCd, skuCd);
		List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchPickingJobList(batch, params);
		
		// 4. 투입한 상품에 대한 투입 순서 조회 시도
		int inputSeq = -1;
		if(ValueUtil.isEmpty(jobList)) {
			String sql = this.dasQueryStore.getDasFindInputSeqBySkuQuery();
			inputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
		}
		
		// 5. 투입할 작업 리스트가 없고 투입된 내역이 없다면 에러   
		if(ValueUtil.isEmpty(jobList) && inputSeq == -1) {
			// 투입한 상품으로 처리할 작업이 없습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_TO_PROCESS_BY_INPUT");
		}
		
		// 6. 이미 투입한 상품이면 검수 - 이미 처리한 작업은 inspect, 처리해야 할 작업은 pick 액션으로 점등
		if(inputSeq >= 1) {
			throw ThrowUtil.newValidationErrorWithNoLog("already-input-sku-want-to-inspection");
		// 7. 투입할 상품이면 투입 처리
		} else {
			// 7.1 투입 처리
			JobInput newInput = this.doInputSku(batch, comCd, skuCd, jobList);
			// 7.2 호기별로 모든 작업 존 별로 현재 '피킹 시작' 상태인 작업이 없다면 그 존은 점등한다.
			this.startAssorting(batch, newInput, jobList);
			// 7.3 투입 후 처리 이벤트 전송
			this.eventPublisher.publishEvent(new InputEvent(newInput, batch.getJobType()));
			// TODO 7.3 이벤트 핸들러에서 아래 코드 사용 - 호기별 메인 모바일 디바이스(태블릿, PDA)에 새로고침 메시지 전달
			this.sendMessageToMobileDevice(batch, null, null, "info", DeviceCommand.COMMAND_REFRESH);
		}
		
		// 8. 작업 리스트 리턴
		return jobList;
	}
	
	/**
	 * 투입 처리 
	 * 
	 * @param batch
	 * @param comCd
	 * @param skuCd
	 * @param jobList
	 * @return
	 */
	private JobInput doInputSku(JobBatch batch, String comCd, String skuCd, List<JobInstance> jobList) {
		// 1. 투입 정보 생성 
		int nextInputSeq = this.serviceDispatcher.getJobStatusService(batch).findNextInputSeq(batch);
		JobInput newInput = new JobInput();
		newInput.setDomainId(batch.getDomainId());
		newInput.setBatchId(batch.getId());
		newInput.setEquipType(LogisConstants.EQUIP_TYPE_RACK);
		newInput.setEquipCd(batch.getEquipCd());
		newInput.setInputSeq(nextInputSeq);
		newInput.setComCd(comCd);
		newInput.setSkuCd(skuCd);
		newInput.setStatus(JobInput.INPUT_STATUS_WAIT);
		// TODO 이전 투입에 대한 컬러 조회
		String prevColor = null;
		String currentColor = this.serviceDispatcher.getIndicationService(batch).nextIndicatorColor(jobList.get(0), prevColor);
		newInput.setColorCd(currentColor);
		this.queryManager.insert(newInput);
		
		// 2. 투입 작업 리스트 업데이트 
		String currentTime = DateUtil.currentTimeStr();
		for(JobInstance job : jobList) {
			job.setStatus(LogisConstants.JOB_STATUS_INPUT);
			job.setInputSeq(nextInputSeq);
			job.setColorCd(currentColor);
			job.setInputAt(currentTime);
			job.setPickStartedAt(currentTime);
			job.setPickingQty(job.getPickQty());
		}
		
		// 3. 작업 정보 업데이트
		this.queryManager.updateBatch(jobList, "status", "pickingQty", "colorCd", "inputSeq", "pickStartedAt", "inputAt", "updaterId", "updatedAt");
		
		// 4. 투입 정보 리턴
		return newInput;
	}
	
	/**
	 * 작업 존에 분류 처리를 위한 표시기 점등
	 * 
	 * @param batch
	 * @param input
	 * @param jobList
	 */
	private void startAssorting(JobBatch batch, JobInput input, List<JobInstance> jobList) {		
		// 배치 호기별로 표시기 점등이 안 되어 있는 존을 조회하여 해당 존의 표시기 리스트를 점등한다.
		List<String> stationList = AnyValueUtil.filterValueListBy(jobList, "stationCd");
		
		// 작업 배치내 작업 존 리스트 내에 피킹 (표시기 점등) 중인 작업이 있는 작업 존 리스트를 조회
		String sql = this.dasQueryStore.getDasSearchWorkingStationQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,inputSeq,status,stationCdList", input.getDomainId(), input.getBatchId(), input.getInputSeq(), LogisConstants.JOB_STATUS_PICKING, stationList);
		List<String> pickingStationList = this.queryManager.selectListBySql(sql, params, String.class, 0, 0);
		
		List<JobInstance> indJobList = new ArrayList<JobInstance>();
		for(JobInstance job : jobList) {
			// 피킹된 (즉 작업 중인) 작업 존 외에 존재하는 작업 리스트를 대상으로 표시기 점등
			if(!pickingStationList.contains(job.getStationCd())) {
				indJobList.add(job);
			}
		}
		
		// 표시기 점등
		if(ValueUtil.isNotEmpty(indJobList)) {
			this.serviceDispatcher.getIndicationService(batch).indicatorsOn(batch, false, indJobList);
		}
	}

	@Override
	public Object inputSkuBundle(IClassifyInEvent inputEvent) {
		throw new ElidomRuntimeException("묶음 투입은 지원하지 않습니다.");
	}

	@Override
	public Object inputSkuBox(IClassifyInEvent inputEvent) {
		throw new ElidomRuntimeException("박스 단위 투입은 지원하지 않습니다.");
	}

	@Override
	public Object inputForInspection(IClassifyInEvent inputEvent) {
		throw new ElidomRuntimeException("검수를 위한 투입은 지원하지 않습니다.");
	}

	@Override
	public void confirmAssort(IClassifyRunEvent exeEvent) { 
		JobInstance job = exeEvent.getJobInstance();
		
		// 1. 확정 처리 할 수 없다면 
		if(job.isDoneJob() || job.getPickedQty() >= job.getPickQty()) {
			this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
			
		// 2. 확정 처리
		} else {
			int resQty = job.getPickingQty();
			job.setPickedQty(job.getPickedQty() + resQty);
			job.setPickingQty(0);
			String status = (job.getPickedQty() >= job.getPickQty()) ?  LogisConstants.JOB_STATUS_FINISH : LogisConstants.JOB_STATUS_PICKING;
			job.setStatus(status);
			this.queryManager.update(job, "pickingQty", "pickedQty", "status", "updatedAt");
			
			// 3. 주문 정보 업데이트 처리
			this.updateOrderPickedQtyByConfirm(job, resQty);
			
			// 4. 다음 작업 처리
			this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));			
		}
	}

	@Override
	public void cancelAssort(IClassifyRunEvent exeEvent) {
		JobInstance job = exeEvent.getJobInstance();
		
		// 1. 이미 작업 취소 상태이면 스킵
		if(ValueUtil.isEqualIgnoreCase(LogisConstants.JOB_STATUS_CANCEL, job.getStatus())) {
			return;
			
		// 2. 상태 체크, 표시기에서 처리했고 이미 완료된 작업이라면 다음 작업 표시기 점등
		} else if(job.isDoneJob()) {
			this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
			
		// 3. 취소 처리
		} else {
			job.setPickingQty(0);
			this.queryManager.update(job, "pickingQty", "updatedAt");
			// 다음 작업 처리
			this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));			
		}
	}

	@Override
	public int splitAssort(IClassifyRunEvent exeEvent) { 
		JobInstance job = exeEvent.getJobInstance();
		int resQty = exeEvent.getResQty();
		
		// 1. 작업 상태 체크, 처리할 수량이 0이면 현재 로케이션에 '피킹 중' 상태 작업 표시기 재점등
		if(resQty == 0 || job.isDoneJob()) {
			this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
			return 0;
			
		// 2. 수량 조절 처리 
		} else {
			int pickedQty = job.getPickedQty() + resQty;
			job.setPickingQty(0);
			job.setPickedQty(pickedQty);
			
			if(job.getPickedQty() >= job.getPickQty()) { 
				job.setStatus(LogisConstants.JOB_STATUS_FINISH);
			}
			
			// 2-1. 수량 조절 처리 
			this.queryManager.update(job, "pickingQty", "pickedQty", "status", "updatedAt");
			
			// 2-2. 주문 정보 업데이트 처리
			this.updateOrderPickedQtyByConfirm(job, resQty);
			
			// 2-3. 다음 작업 처리
			this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
			
			// 2-4. 조정 수량 리턴 
			return resQty;
		}
	}

	@Override
	public int undoAssort(IClassifyRunEvent exeEvent) {
		// 1. 작업 데이터 확정 수량 0으로 업데이트 
		JobInstance job = exeEvent.getJobInstance();
		int pickedQty = job.getPickedQty();
		job.setPickingQty(0);
		job.setPickedQty(0);
		this.queryManager.update(job, "pickingQty", "pickedQty", "updatedAt");
		
		// 2. 주문 데이터 확정 수량 마이너스 처리
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId(), 1, 1);
		condition.addFilter("batchId", job.getId());
		condition.addFilter("equipCd", job.getEquipCd());
		// 설정에서 셀 - 박스와 매핑될 타겟 필드를 조회  
		String classFieldName = DasBatchJobConfigUtil.getBoxMappingTargetField(exeEvent.getJobBatch());
		condition.addFilter(classFieldName, job.getClassCd());
		condition.addFilter("status", "in", ValueUtil.toList(Order.STATUS_RUNNING, Order.STATUS_FINISHED));
		condition.addFilter("pickingQty", ">=", pickedQty);
		condition.addOrder("updatedAt", false);
		
		List<Order> orderList = this.queryManager.selectList(Order.class, condition);
		if(ValueUtil.isNotEmpty(orderList)) {
			Order order = orderList.get(0);
			order.setPickedQty(order.getPickedQty() - pickedQty);
			order.setStatus(Order.STATUS_RUNNING);
			this.queryManager.update(order, "pickedQty", "status", "updatedAt");
		}
		
		// 3. 다음 작업 처리
		this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
		
		// 4. 주문 취소된 확정 수량 리턴
		return pickedQty;
	}

	@EventListener(classes = IClassifyOutEvent.class, condition = "#outEvent.jobType == 'DAS'")
	@Override
	public BoxPack fullBoxing(IClassifyOutEvent outEvent) {

		// 1. 작업 데이터 추출
		JobInstance job = outEvent.getJobInstance();
		
		// 2. 풀 박스 체크
		if(ValueUtil.isEqualIgnoreCase(LogisConstants.JOB_STATUS_BOXED, job.getStatus())) {
			throw ThrowUtil.newValidationErrorWithNoLog("작업[" + job.getId() + "]은 이미 풀 박스가 완료되었습니다.");
		}
		
		// 3. 풀 박스 처리 
		BoxPack boxPack = this.boxService.fullBoxing(outEvent.getJobBatch(), outEvent.getWorkCell(), ValueUtil.toList(job), this);
		
		// 4. 다음 작업 처리
		if(boxPack != null) {
			this.doNextJob(job, outEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
		}
		
		// 5. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack partialFullboxing(IClassifyOutEvent outEvent) {
		// 1. 작업 데이터 추출
		JobInstance job = outEvent.getJobInstance();
		
		// 2. 풀 박스 체크
		if(ValueUtil.isEqualIgnoreCase(LogisConstants.JOB_STATUS_BOXED, job.getStatus())) {
			throw ThrowUtil.newValidationErrorWithNoLog("작업[" + job.getId() + "]은 이미 풀 박스가 완료되었습니다.");
		}
		
		// 3. 풀 박스 처리
		int resQty = outEvent.getReqQty();
		BoxPack boxPack = this.boxService.partialFullboxing(outEvent.getJobBatch(), outEvent.getWorkCell(), ValueUtil.toList(job), resQty, this);
		
		// 4. 다음 작업 처리
		if(boxPack != null) {
			this.doNextJob(job, outEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
		}
		
		// 5. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack cancelBoxing(Long domainId, BoxPack box) {
		// 1. 풀 박스 취소 전 처리
		if(box == null) {
			throw ThrowUtil.newValidationErrorWithNoLog("박싱 취소할 박스가 없습니다.");
		}
		
		// 2. 풀 박스 취소 
		BoxPack boxPack = this.boxService.cancelFullboxing(box);
		
		// 3. 박스 리턴
		return boxPack;
	}

	@Override
	public JobInstance splitJob(JobInstance job, WorkCell workCell, int splitQty) {
		// 1. 작업 분할이 가능한 지 체크
		if(job.getPickQty() - splitQty < 0) {
			throw new ElidomRuntimeException("예정수량보다 분할수량이 커서 작업분할 처리를 할 수 없습니다.");
		}
		
		// 2. 기존 작업 데이터 복사 
		JobInstance boxedJob = AnyValueUtil.populate(job, new JobInstance());
		String nowStr = DateUtil.currentTimeStr();
		int pickedQty = job.getPickedQty() - splitQty;
		
		// 3. 새 작업 데이터의 수량 및 상태 변경
		boxedJob.setId(AnyValueUtil.newUuid36());
		boxedJob.setPickQty(splitQty);
		boxedJob.setPickingQty(0);
		boxedJob.setPickedQty(splitQty);
		boxedJob.setBoxedAt(nowStr);
		boxedJob.setPickEndedAt(nowStr);
		boxedJob.setStatus(LogisConstants.JOB_STATUS_BOXED);
		this.queryManager.insert(boxedJob);
		 
		// 4. 기존 작업 데이터의 수량 및 상태 변경 
		job.setPickQty(job.getPickQty() - splitQty);
		job.setPickedQty(pickedQty);
		job.setStatus(pickedQty > 0 ? LogisConstants.JOB_STATUS_PICKING : LogisConstants.JOB_STATUS_WAIT);
		this.queryManager.update(job, "pickQty", "pickedQty", "status", "updatedAt");	
		
		// 5. 기존 작업 데이터 리턴
		return job;
	}
	
	/**
	 * 다음 작업 처리
	 * 
	 * @param job
	 * @param cell
	 * @param cellEndFlag
	 */
	protected void doNextJob(JobInstance job, WorkCell cell, boolean cellEndFlag) {
		// 1. 해당 로케이션의 작업이 모두 완료 상태인지 체크 
		if(cellEndFlag) {
			this.finishAssortCell(job, cell, cellEndFlag);
			
		// 2. 현재 로케이션에 존재하는 '피킹 시작' 상태 작업의 '피킹 중 수량' 정보를 조회하여 표시기 재점등
		} else {
			if(job.getPickingQty() > 0) {
				this.serviceDispatcher.getIndicationService(job).indicatorOnForPick(job, 0, job.getPickingQty(), 0);
			}
		}
	}
	
	@Override
	public JobInstance findLatestJobForBoxing(Long domainId, String batchId, String cellCd) {
		// 박싱 처리를 위해 로케이션에 존재하는 박스 처리할 작업을 조회
		String sql = "select * from (select * from job_instances where domain_id = :domainId and batch_id = :batchId and sub_equip_cd = :cellCd and status in (:statuses) order by pick_ended_at desc) where rownum <= 1";
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,cellCd,statuses", domainId, batchId, cellCd, LogisConstants.JOB_STATUS_PF);
		return this.queryManager.selectBySql(sql, params, JobInstance.class);
	}

	@Override
	public boolean checkCellAssortEnd(JobInstance job, boolean finalEndCheck) {
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId());
		condition.addFilter("batchId", job.getBatchId());
		condition.addFilter("subEquipCd", job.getSubEquipCd());
		List<String> statuses = finalEndCheck ? LogisConstants.JOB_STATUS_WIPFC : LogisConstants.JOB_STATUS_WIPC;
		condition.addFilter("status", SysConstants.IN, statuses);
		return this.queryManager.selectSize(JobInstance.class, condition) == 0;
	}

	@Override
	public boolean finishAssortCell(JobInstance job, WorkCell workCell, boolean finalEndFlag) {
	    // 1. 로케이션 분류 최종 완료 상태인지 즉 더 이상 박싱 처리할 작업이 없는지 체크 
		boolean finalEnded = this.checkCellAssortEnd(job, finalEndFlag);
	    
		// 2. 로케이션에 완료 상태 기록
		String cellJobStatus = finalEnded ? LogisConstants.CELL_JOB_STATUS_ENDED : LogisConstants.CELL_JOB_STATUS_ENDING;
		workCell.setStatus(cellJobStatus);
		if(!finalEnded) { 
			workCell.setJobInstanceId(job.getId()); 
		}
		this.queryManager.update(workCell, "status", "jobInstanceId", "updatedAt");
		
		// 3. 표시기에 분류 처리 내용 표시
		this.serviceDispatcher.getIndicationService(job).indicatorOnForPickEnd(job, finalEnded);
		return true;
	}
	
	@Override
	public boolean checkEndClassifyAll(JobBatch batch) {
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("statuses", SysConstants.IN, LogisConstants.JOB_STATUS_WIPC);
		return this.queryManager.selectSize(JobInstance.class, condition) == 0;
	}
	
	@Override
	public void handleClassifyException(IClassifyErrorEvent errorEvent) {
		// 1. 예외 정보 추출 
		Throwable th = errorEvent.getException();
		// 2. 디바이스 정보 추출
		String device = errorEvent.getClassifyRunEvent().getClassifyDevice();
		// 3. 표시기로 부터 온 요청이 에러인지 체크
		boolean isIndicatorDevice = !ValueUtil.isEqualIgnoreCase(device, Indicator.class.getSimpleName());

		// 4. 모바일 알람 이벤트 전송
		if(th != null) {
			String cellCd = (errorEvent.getWorkCell() != null) ? errorEvent.getWorkCell().getCellCd() : (errorEvent.getJobInstance() != null ? errorEvent.getJobInstance().getSubEquipCd() : null);
			String stationCd = ValueUtil.isNotEmpty(cellCd) ? 
				AnyEntityUtil.findEntityBy(errorEvent.getDomainId(), false, String.class, "stationCd", "domainId,cellCd", errorEvent.getDomainId(), cellCd) : null;
			
			String errMsg = (th.getCause() == null) ? th.getMessage() : th.getCause().getMessage();
			this.sendMessageToMobileDevice(errorEvent.getJobBatch(), isIndicatorDevice ? null : device, stationCd, "error", errMsg);			
		}

		// 5. 예외 발생
		throw (th instanceof ElidomException) ? (ElidomException)th : new ElidomRuntimeException(th);
	}
	
	@Override
	public boolean checkStationJobsEnd(JobInstance job, String stationCd) {
		// 반품에서는 사용 안 함 
		return false;
	}
	
	/**
	 * 분류 확정 처리시에 작업 정보에 매핑되는 주문 정보를 찾아서 확정 수량 업데이트 
	 *
	 * @param job
	 * @param totalPickedQty
	 */
	private void updateOrderPickedQtyByConfirm(JobInstance job, int totalPickedQty) {
		// 1. 주문 정보 조회		
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId());
		condition.addFilter("batchId",	job.getBatchId()); 
		condition.addFilter("skuCd",	job.getSkuCd());
		condition.addFilter("status",	SysConstants.IN,	ValueUtil.toList(LogisConstants.COMMON_STATUS_RUNNING, LogisConstants.COMMON_STATUS_WAIT));
		condition.addOrder("orderNo",	true);
		condition.addOrder("pickedQty",	false);
		List<Order> sources = this.queryManager.selectList(Order.class, condition);   
 		
		// 2. 주문에 피킹 확정 수량 업데이트
		for(Order source : sources) {
			if(totalPickedQty > 0) {
				int orderQty = source.getOrderQty();
				int pickedQty = source.getPickedQty();
				int remainQty = orderQty - pickedQty;
				
				// 2-1. 주문 처리 수량 업데이트 및 주문 라인 분류 종료
				if(totalPickedQty >= remainQty) {
					source.setPickedQty(source.getPickedQty() + remainQty);
					source.setStatus(LogisConstants.COMMON_STATUS_FINISHED);  
					totalPickedQty = totalPickedQty - remainQty;
					
				// 2-2. 주문 처리 수량 업데이트
				} else if(remainQty > totalPickedQty) {
					source.setPickedQty(source.getPickedQty() + totalPickedQty);
					totalPickedQty = 0; 
				} 
				
				this.queryManager.update(source, "pickedQty", "status", "updatedAt");
				
			} else {
				break;
			}			
		}
	}
	
	/**
	 * 모바일 디바이스에 메시지 전송
	 * 
	 * @param batch
	 * @param toDevice
	 * @param stationCd
	 * @param notiType
	 * @param message
	 */
	private void sendMessageToMobileDevice(JobBatch batch, String toDevice, String stationCd, String notiType, String message) {
		String[] deviceList = null;
		
		if(toDevice == null) {
			// toDevice가 없다면 사용 디바이스 리스트 조회
			deviceList = DasBatchJobConfigUtil.getDeviceList(batch) == null ? null : DasBatchJobConfigUtil.getDeviceList(batch);
		} else {
			deviceList = new String[] { toDevice };
		}
		
		if(deviceList != null) {
			for(String device : deviceList) {
				this.serviceDispatcher.getDeviceService().sendMessageToDevice(batch.getDomainId(), device, batch.getStageCd(), batch.getEquipType(), batch.getEquipCd(), stationCd, null, batch.getJobType(), notiType, message, null);
			}
		}
	}

}
