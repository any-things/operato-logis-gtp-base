package operato.logis.gtp.base.service.rtn;
     
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.ICategorizeEvent;
import xyz.anythings.base.event.IClassifyErrorEvent;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.IClassifyOutEvent;
import xyz.anythings.base.event.IClassifyRunEvent;
import xyz.anythings.base.event.classfy.ClassifyErrorEvent;
import xyz.anythings.base.event.classfy.ClassifyRunEvent;
import xyz.anythings.base.model.Category;
import xyz.anythings.base.service.api.IAssortService;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.impl.AbstractClassificationService;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.gw.entity.Indicator;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.ElidomException;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.util.ValueUtil; 



/**
 * 반품 분류 서비스  구현
 *
 * @author shortstop
 */
@Component("rtnAssortService")
public class RtnAssortService extends AbstractClassificationService implements IAssortService {

	/**
	 * 박스 서비스
	 */
	@Autowired
	private RtnBoxingService boxService;
	
	@Override
	public String getJobType() {
		return LogisConstants.JOB_TYPE_RTN;
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
		// TODO 작업 지시 시점에 반품 관련 추가 처리 - Ex) 할당된 표시기에 점등 ... 
	}

	@Override
	public void batchCloseAction(JobBatch batch) {
		// 1. 모든 셀에 남아 있는 잔량에 대해 풀 박싱 
		// this.boxService.batchBoxing(batch);
	}

	@Override
	public Category categorize(ICategorizeEvent event) {
		// TODO 중분류 
		return null;
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
			throw new ElidomRuntimeException("스캔한 정보는 어떤 코드 유형 인지 구분할 수 없습니다.");
		}
	}

	@Override
	public Object input(IClassifyInEvent inputEvent) { 
		return this.inputSkuSingle(inputEvent); 
		
	} 
	
	@EventListener(classes = ClassifyRunEvent.class, condition = "#exeEvent.jobType == 'RTN'")
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
				//case LogisCodeConstants.CLASSIFICATION_ACTION_FULL :
				//	this.fullBoxing(exeEvent);
				//	break;
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
		JobBatch batch = inputEvent.getJobBatch(); 
		String comCd = inputEvent.getComCd();
		String skuCd = inputEvent.getInputCode();
		Integer inputQty = inputEvent.getInputQty();
		String nowStr = DateUtil.currentTimeStr();
		
		// 1. 투입된 상품 코드를 제외하고 투입 이후 확정 처리가 안 된 상품을 조회 
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("comCd", comCd);
		condition.addFilter("skuCd", "noteq", skuCd);
		condition.addFilter("equipCd", batch.getEquipCd());
		condition.addFilter("status", LogisConstants.JOB_STATUS_PICKING);
		condition.addFilter("pickingQty", ">=", 1);
		
		if(this.queryManager.selectSize(JobInstance.class, condition) > 0) { 
			throw new ElidomRuntimeException("투입 이후 확정 처리를 안 한 셀이 있습니다.");
		}
		
		// 2. 작업 인스턴스 조회 
		condition.removeFilter("skuCd");
		condition.removeFilter("status");
		condition.removeFilter("pickingQty");
		
		condition.addFilter("skuCd", skuCd);
		condition.addFilter("status", "in", LogisConstants.JOB_STATUS_WIPC);
		JobInstance job = this.queryManager.selectByCondition(JobInstance.class, condition);
		Integer pickingQty = job.getPickingQty() + inputQty;
	 
		// 3. 작업 인스턴스 정보 업데이트
		if(job.getPickQty() >= (job.getPickedQty() + pickingQty)) {
			// 3-1. TODO 배치내 작업 데이터 중에 Max InputSeq 조회 후 + 1
			job.setInputSeq(1);
			job.setPickingQty(pickingQty);
			job.setStatus(LogisConstants.JOB_STATUS_PICKING);
			
			if(ValueUtil.isEmpty(job.getPickStartedAt())) {
				job.setPickStartedAt(nowStr);
			}
			
			if(ValueUtil.isEmpty(job.getInputAt())) {
				job.setInputAt(nowStr);
			} 
			
			job.setColorCd(LogisConstants.COLOR_RED);
			this.queryManager.update(job, "inputSeq", "pickingQty", "status", "pickStartedAt", "inputAt", "colorCd", "updatedAt");
			
			// 3-2 표시기 점등
			this.serviceDispatcher.getIndicationService(batch).indicatorOnForPick(job, 0, job.getPickingQty(), 0);
			
		} else {
			// 처리 가능한 수량 초과.
			throw new ElidomRuntimeException("처리 예정 수량을 초과 했습니다.");
		} 
		 
		return job;
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
		
		// 2. TODO 주문 데이터 확정 수량 마이너스 처리 
		
		// 3. 다음 작업 처리
		this.doNextJob(job, exeEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
		
		// 4. 주문 취소된 확정 수량 리턴
		return pickedQty;
	}

	@EventListener(classes = IClassifyOutEvent.class, condition = "#outEvent.jobType == 'RTN'")
	@Override
	public BoxPack fullBoxing(IClassifyOutEvent outEvent) {
		JobInstance job = outEvent.getJobInstance();
		// 1. TODO 풀 박스 전 처리
		
		// 2. 풀 박스 처리 
		BoxPack boxPack = this.boxService.fullBoxing(outEvent.getJobBatch(), outEvent.getWorkCell(), ValueUtil.toList(job), this);
		
		// 3. 다음 작업 처리
		this.doNextJob(job, outEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
		
		// 4. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack partialFullboxing(IClassifyOutEvent outEvent) {
		JobInstance job = outEvent.getJobInstance();
		// 1. TODO 풀 박스 전 처리
		
		// 2. 풀 박스 처리
		int resQty = outEvent.getReqQty();
		BoxPack boxPack = this.boxService.partialFullboxing(outEvent.getJobBatch(), outEvent.getWorkCell(), ValueUtil.toList(job), resQty, this);
		
		// 3. 다음 작업 처리
		this.doNextJob(job, outEvent.getWorkCell(), this.checkCellAssortEnd(job, false));
		
		// 4. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack cancelBoxing(Long domainId, BoxPack box) {
		// 1. TODO 풀 박스 취소 전 처리
		
		// 2. 풀 박스 취소 
		BoxPack boxPack = this.boxService.cancelFullboxing(box);
		
		// 3. 다음 작업 처리
		JobInstance job = this.findLatestJobForBoxing(box.getDomainId(), box.getBatchId(), box.getSubEquipCd());
		WorkCell cell = AnyEntityUtil.findEntityBy(domainId, true, WorkCell.class, "domainId,batchId,cellCd", box.getDomainId(), box.getBatchId(), box.getSubEquipCd());
		this.doNextJob(job, cell, this.checkCellAssortEnd(job, false));
		
		// 4. 박스 리턴
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
		String cellJobStatus = finalEnded ? LogisConstants.CELL_JOB_STATUS_ENDED : LogisConstants.CELL_JOB_STATUS_END;
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
		Throwable th = errorEvent.getException();
		String device = errorEvent.getClassifyRunEvent().getClassifyDevice();
		// 1. 모바일 디바이스로 메시지 전송 여부 
		boolean sendMessageToDevice = !ValueUtil.isEqualIgnoreCase(device, Indicator.class.getSimpleName());

		// 2. 모바일 알람 이벤트 전송 -> 호기에 태블릿 혹은 PDA로 에러 메시지 전달
		if((th != null) && sendMessageToDevice) {
			String errMsg = (th.getCause() == null) ? th.getMessage() : th.getCause().getMessage();
			JobInstance job = errorEvent.getJobInstance();
			this.sendMessageToMobileDevice(errorEvent.getDomainId(), LogisConstants.JOB_TYPE_RTN, job.getEquipCd(), null, "error", errMsg);
		}

		// 3. 예외 발생
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
	 * @param domainId
	 * @param jobType
	 * @param equipCd
	 * @param stationCd
	 * @param notiType
	 * @param message
	 */
	private void sendMessageToMobileDevice(Long domainId, String jobType, String equipCd, String stationCd, String notiType, String message) {
		// String equipType = MpsCompanySetting.getMainMobileDevice(domainId, null, jobType);
		// MobileNotiEvent event = new MobileNotiEvent(domainId, jobType, equipType, equipCd, stationCd, notiType, message);
		// this.eventPublisher.publishEvent(event);
	}

}
