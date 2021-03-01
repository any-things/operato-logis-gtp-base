package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.DasQueryStore;
import operato.logis.das.service.api.IDasIndicationService;
import operato.logis.das.service.util.DasBatchJobConfigUtil;
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.entity.SKU;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.ICategorizeEvent;
import xyz.anythings.base.event.IClassifyErrorEvent;
import xyz.anythings.base.event.IClassifyEvent;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.IClassifyOutEvent;
import xyz.anythings.base.event.IClassifyRunEvent;
import xyz.anythings.base.event.classfy.ClassifyEndEvent;
import xyz.anythings.base.event.classfy.ClassifyErrorEvent;
import xyz.anythings.base.event.classfy.ClassifyRunEvent;
import xyz.anythings.base.event.device.DeviceEvent;
import xyz.anythings.base.event.input.InputEvent;
import xyz.anythings.base.model.Category;
import xyz.anythings.base.model.CategoryItem;
import xyz.anythings.base.query.store.IndicatorQueryStore;
import xyz.anythings.base.service.api.IAssortService;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.api.IIndicationService;
import xyz.anythings.base.service.api.IJobStatusService;
import xyz.anythings.base.service.impl.AbstractClassificationService;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.base.service.util.RuntimeIndServiceUtil;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.gw.entity.IndConfigSet;
import xyz.anythings.gw.entity.Indicator;
import xyz.anythings.gw.event.GatewayInitEvent;
import xyz.anythings.gw.service.IndConfigProfileService;
import xyz.anythings.sys.service.ICustomService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.ElidomException;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.SettingUtil;
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
	 * 상품 투입 처리 후 커스텀 서비스
	 */
	private static final String DIY_INPUT_SKU_ACTION = "diy-das-input-sku";
	/**
	 * 피킹 완료 처리 후 커스텀 서비스
	 */
	private static final String DIY_PICKED_ACTION = "diy-das-pick-end";
	/**
	 * 셀 주문 완료 처리 후 커스텀 서비스
	 */
	private static final String DIY_CELL_END_ACTION = "diy-das-cell-end";
	/**
	 * 피킹 취소 후 커스텀 서비스
	 */
	private static final String DIY_CANCEL_PICK_ACTION = "diy-cancel-picking";
	
	/**
	 * 표시기 설정 프로파일 서비스
	 */
	@Autowired
	private IndConfigProfileService indConfigSetService;
	/**
	 * 박스 서비스
	 */
	@Autowired
	private DasBoxingService boxService;
	/**
	 * 커스텀 서비스
	 */
	@Autowired
	private ICustomService customService;
	/**
	 * DAS 쿼리 스토어
	 */
	@Autowired
	private DasQueryStore dasQueryStore;
	/**
	 * DAS 표시기 관련 쿼리 스토어
	 */
	@Autowired
	private IndicatorQueryStore indQueryStore;
	
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
	
	@EventListener(classes = GatewayInitEvent.class, condition = "#gwInitEvent.eventStep == 2")
	public void handleGatewayInitReport(GatewayInitEvent gwInitEvent) {
		// 1. Gateway 정보 추출
		Gateway gateway = gwInitEvent.getGateway();
		
		if(gateway != null) {
			// 2. Gateway 정보로 호기 리스트 추출
			Long domainId = gwInitEvent.getDomainId();
			String sql = "select rack_cd, batch_id from racks where domain_id = :domainId and job_type = :jobType and rack_cd in (select distinct(equip_cd) as equip_cd from cells where domain_id = :domainId and ind_cd in (select ind_cd from indicators where domain_id = :domainId and gw_cd = :gwCd) order by equip_cd)";
			List<Rack> rackList = this.queryManager.selectListBySql(sql, ValueUtil.newMap("domainId,jobType,gwCd", domainId, LogisConstants.JOB_TYPE_DAS, gateway.getGwCd()), Rack.class, 0, 0);
			
			// 3. 호기로 부터 현재 작업 중인 배치 추출
			for(Rack rack : rackList) {
				// 3-1. 호기 체크
				if(ValueUtil.isNotEmpty(rack.getBatchId())) {
					// 3-2. 작업 배치 조회
					JobBatch batch = AnyEntityUtil.findEntityById(false, JobBatch.class, rack.getBatchId());
					// 3-3. 작업 배치 상태 체크
					if(batch != null && ValueUtil.isEqual(batch.getStatus(), JobBatch.STATUS_RUNNING)) {
						// 3-4. 호기 코드, 게이트웨이 코드로 표시기 이전 상태 복원
						this.restoreMpiOn(batch, gateway, rack.getRackCd());
					}
				}
			}
		}
	}
	
	/**
	 * 작업 배치, 게이트웨이, 호기별로 이전 작업 리스트 표시기 점등
	 * 
	 * @param batch
	 * @param gw
	 * @param rackCd
	 */
	public void restoreMpiOn(JobBatch batch, Gateway gw, String rackCd) {
		if(ValueUtil.isEqual(batch.getStatus(), JobBatch.STATUS_RUNNING)) {
			Long domainId = batch.getDomainId();
			
			// 1. 배치, 게이트웨이에 걸린 모든 진행 중이던 작업 리스트를 조회 ...
			IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
			Map<String, Object> condition = ValueUtil.newMap("domainId,batchId,equipCd,gwCd,status,stageCd,jobType", domainId, batch.getId(), rackCd, gw.getGwCd(), LogisConstants.JOB_STATUS_PICKING, batch.getStageCd(), batch.getJobType());
			IJobStatusService jobStatusSvc = this.serviceDispatcher.getJobStatusService(batch);
			List<JobInstance> jobList = jobStatusSvc.searchPickingJobList(batch, condition);

			// 2. 작업 중이던 작업 표시기 재점등 ...
			indSvc.indicatorsOn(batch, true, jobList);
			
			// 3. 배치, 게이트웨이에 걸린 셀 중에 ENDING, ENDED 상태인 셀을 모두 조회
			condition.put("gwPath", gw.getGwNm());
			condition.put("indStatuses", LogisConstants.CELL_JOB_STATUS_END_LIST);
			String sql = this.dasQueryStore.getRestoreEndIndicators();
			jobList = this.queryManager.selectListBySql(sql, condition, JobInstance.class, 0, 0);
			
			// 4. ENDING, ENDED 조회한 정보로 모두 점등
			if(ValueUtil.isNotEmpty(jobList)) {
				for(JobInstance job : jobList) {
					indSvc.indicatorOnForPickEnd(job, ValueUtil.isEqualIgnoreCase(job.getStatus(), LogisConstants.CELL_JOB_STATUS_ENDED));
				}
			}
		}
	}
	
	@Override
	public void batchStartAction(JobBatch batch) {
		// 1. 작업 설정 셋 추가 
		this.serviceDispatcher.getConfigSetService().addConfigSet(batch);
		
		// 2. 표시기 설정 셋 추가
		IndConfigSet configSet = batch.getIndConfigSet() != null ? batch.getIndConfigSet() : (ValueUtil.isEmpty(batch.getIndConfigSetId()) ? null : this.queryManager.select(IndConfigSet.class, batch.getIndConfigSetId()));
		this.indConfigSetService.addConfigSet(batch.getId(), configSet);
		
		// 3. 설정에서 작업배치 시에 게이트웨이 리부팅 할 지 여부 조회
		boolean gwReboot = DasBatchJobConfigUtil.isGwRebootWhenInstruction(batch);
		IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
		
		if(gwReboot) {
			List<Gateway> gwList = indSvc.searchGateways(batch);
			
			// 게이트웨이 리부팅 처리
			for(Gateway gw : gwList) {
				indSvc.rebootGateway(batch, gw);
			}
		}
		
		// 4. 설정에서 작업 지시 시점에 박스 매핑 표시 여부 조회
		if(DasBatchJobConfigUtil.isIndOnAssignedCellWhenInstruction(batch)) {
			// 게이트웨이 리부팅 시에는 리부팅 프로세스 완료시까지 약 1분여간 기다린다.
			if(gwReboot) {
				int sleepTime = DasBatchJobConfigUtil.getWaitDuarionIndOnAssignedCellWhenInstruction(batch);
				if(sleepTime > 0) {
					ThreadUtil.sleep(sleepTime * 1000);
				}
			}
			
			// 표시기에 박스 매핑 표시
			((IDasIndicationService)indSvc).displayAllForBoxMapping(batch);
		
		// 5. 작업 지시 시점에 표시기에 로케이션 정보 표시할 지 여부 조회
		} else if(DasBatchJobConfigUtil.isIndOnCellCodeWhenInstruction(batch)) {
			// 게이트웨이 리부팅 시에는 리부팅 프로세스 완료시까지 약 1분여간 기다린다.
			if(gwReboot) {
				int sleepTime = DasBatchJobConfigUtil.getWaitDuarionIndOnAssignedCellWhenInstruction(batch);
				if(sleepTime > 0) {
					ThreadUtil.sleep(sleepTime * 1000);
				}
			}
			
			// 배치 소속 게이트웨이 조회
			List<Gateway> gwList = indSvc.searchGateways(batch);
			
			// 게이트웨이 별 표시기에 셀 코드 표시 처리
			for(Gateway gw : gwList) {
				String sql = "select c.ind_cd, c.cell_cd from cells c inner join indicators i on c.domain_id = i.domain_id and c.ind_cd = i.ind_cd where c.domain_id = :domainId and i.gw_cd = :gwCd order by c.ind_seq asc";
				Map<String, Object> params = ValueUtil.newMap("domainId,gwCd", batch.getDomainId(), gw.getGwCd());
				List<Cell> cellList = this.queryManager.selectListBySql(sql, params, Cell.class, 0, 0);
				for(Cell cell : cellList) {
					indSvc.displayForCellCd(batch.getDomainId(), batch.getId(), batch.getStageCd(), batch.getJobType(), gw.getGwNm(), cell.getIndCd(), cell.getCellCd());
				}
			}
		}
	}
	
	@Override
	public void batchCloseAction(JobBatch batch) {
		// 1. 모든 셀에 남아 있는 잔량에 대해 풀 박싱 여부 조회
		if(DasBatchJobConfigUtil.isBatchFullboxWhenClosingEnabled(batch)) {
			// 배치 풀 박싱
			this.boxService.batchBoxing(batch);
		}
		
		// 2. 표시기 설정 셋 제거
		this.indConfigSetService.clearConfigSet(batch.getId());
		
		// 3. 작업 설정 셋 제거 
		this.serviceDispatcher.getConfigSetService().clearConfigSet(batch.getId());
	}

	@Override
	@EventListener(classes = ICategorizeEvent.class, condition = "#event.jobType == 'DAS'")
	public Category categorize(ICategorizeEvent event) {
		// 중분류 수량을 분류 처리한 수량에서 제외할 건지 여부 설정
		// boolean fixedQtyMode = DasBatchJobConfigUtil.isCategorizationDisplayFixedQtyMode(batch);
		// boolean regSortAsc = DasBatchJobConfigUtil.isCategorizationRackSortMode(batch);
		
		String comCd = event.getComCd();
		String skuCd = event.getInputCode();
		List<String> batchIdList = event.getBatchIdList();
		
		Category category = new Category();
		category.setSkuCd(event.getInputCode());
		String sql = this.dasQueryStore.getDasCategorizationQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,comCd,skuCd,batchIdList", event.getDomainId(), comCd, skuCd, batchIdList);
		List<CategoryItem> items = this.queryManager.selectListBySql(sql, params, CategoryItem.class, 0, 0);
		category.setItems(items);
		
		event.setResult(category);
		event.setExecuted(true);
		return category;
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

	/**
	 * 투입 시 전체 상품에 대한 표시기 점등
	 * 
	 * @param inputEvent
	 * @return
	 */
	private List<JobInstance> indOnByAllMode(IClassifyInEvent inputEvent) {
		// 1. 이벤트에서 데이터 추출 후 투입 상품 코드 Validation & 상품 투입 시퀀스 조회
		JobBatch batch = inputEvent.getJobBatch();
		String comCd = inputEvent.getComCd();
		String skuCd = inputEvent.getInputCode();
		int inputSeq = this.validateInputSKU(batch, comCd, skuCd);

		// 2. 이미 투입한 상품이면 검수 - 이미 처리한 작업은 inspect, 처리해야 할 작업은 pick 액션으로 점등
		if(inputSeq >= 1) {
			throw ThrowUtil.newValidationErrorWithNoLog("already-input-sku-want-to-inspection");
			
		// 3. 투입할 상품이면 투입 처리
		} else {
			// 3.1. 작업 인스턴스 조회
			List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchPickingJobList(batch, ValueUtil.newMap("comCd,skuCd", comCd, skuCd));
			
			// 3.2. 투입할 작업 리스트가 없고 투입된 내역이 없다면 에러
			if(ValueUtil.isEmpty(jobList)) {
				// 투입한 상품으로 처리할 작업이 없습니다
				throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_TO_PROCESS_BY_INPUT");
			}
			
			// 3.3 투입 정보 생성
			JobInput newInput = this.doInputSku(batch, comCd, skuCd, jobList);
			// 3.4 호기별로 모든 작업 존 별로 현재 '피킹 시작' 상태인 작업이 없다면 그 존은 점등한다.
			this.startAssorting(batch, newInput, jobList);
			// 3.5 투입 후 처리 이벤트 전송
			this.eventPublisher.publishEvent(new InputEvent(batch, newInput));
			// 3.6 작업 리스트 리턴
			return jobList;
		}
	}
	
	/**
	 * 투입 시 투입 수량 만큼만 표시기 점등
	 * 
	 * @param inputEvent
	 * @return
	 */
	private List<JobInstance> indOnByQtyMode(IClassifyInEvent inputEvent) {
		// TODO 
		return null;
	}
	
	@Override
	public Object inputSkuSingle(IClassifyInEvent inputEvent) {
		// 상품 투입시 표시기 점등 모드에 따라 표시기 점등
		if(BatchJobConfigUtil.isInputIndOnAllMode(inputEvent.getJobBatch())) {
			// 한 꺼번에 모든 상품 점등
			return this.indOnByAllMode(inputEvent);
		} else {
			// 투입 수량 만큼만 점등
			return this.indOnByQtyMode(inputEvent);
		}
	}

	@Override
	public Object inputSkuBundle(IClassifyInEvent inputEvent) {
		// 묶음 투입 기능 활성화 여부 체크 
		if(!BatchJobConfigUtil.isBundleInputEnabled(inputEvent.getJobBatch())) {
			// 묶음 투입은 지원하지 않습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NOT_SUPPORTED_METHOD");
		// 수량 기반 표시기 점등 모드로 상품 투입
		} else {
			return this.indOnByQtyMode(inputEvent);
		}
	}

	@Override
	public Object inputSkuBox(IClassifyInEvent inputEvent) {
		// 완박스 투입 기능 활성화 여부 체크 
		if(!BatchJobConfigUtil.isSingleBoxInputEnabled(inputEvent.getJobBatch())) {
			// 박스 단위 투입은 지원하지 않습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NOT_SUPPORTED_METHOD");
		// 수량 기반 표시기 점등 모드로 상품 투입
		} else {
			return this.indOnByQtyMode(inputEvent);
		}
	}

	@Override
	public Object inputForInspection(IClassifyInEvent inputEvent) {
		// 1. 현재 작업 배치에 존재하는 상품인지 체크 & 상품 투입 시퀀스 조회
		JobBatch batch = inputEvent.getJobBatch();
		String comCd = inputEvent.getComCd();
		String skuCd = inputEvent.getInputCode();
		int inputSeq = this.validateInputSKU(batch, comCd, skuCd);
		
		// 2. 투입할 작업 리스트가 없고 투입된 내역이 없다면 에러
		if(inputSeq == -1) {
			// 투입한 상품으로 처리할 작업이 없습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_TO_PROCESS_BY_INPUT");
		} else {
			// 현재 스캔한 상품 외에 다른 상품이 '피킹 중' 상태가 있는지 체크, 있으면 예외 발생
			Map<String, Object> params = ValueUtil.newMap("status,overInputSeq", LogisConstants.JOB_STATUS_PICKING, inputSeq);
			List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchPickingJobList(batch, params);
			if(ValueUtil.isNotEmpty(jobList)) {
				JobInstance unpickJob = jobList.get(0);
				String msg = MessageUtil.getMessage("NOT_BEEN_COMPLETED_AFTER_INPUT", "투입한 후 완료 처리를 안 한 작업이 있습니다");
				StringJoiner buffer = new StringJoiner(SysConstants.LINE_SEPARATOR)
						.add(msg)
						.add("[" + unpickJob.getInputSeq() + "]")
						.add("[" + unpickJob.getSubEquipCd() + "]")
						.add("[" + unpickJob.getSkuNm() + "]")
						.add("[" + unpickJob.getPickQty() + "/" + unpickJob.getPickedQty() + "]");
				throw ThrowUtil.newValidationErrorWithNoLog(buffer.toString());
			}
		}
		
		// 4. 투입 순서로 부터 검수
		return this.indOnByInspection(batch, comCd, skuCd, inputSeq);
	}

	@Override
	public void confirmAssort(IClassifyRunEvent exeEvent) {
		// 1. 이벤트로 부터 작업에 필요한 데이터 추출
		JobBatch batch = exeEvent.getJobBatch();
		JobInstance job = exeEvent.getJobInstance();
		int resQty = job.getPickingQty();
		
		// 2. 확정 처리
		if(job.isTodoJob() && job.getPickedQty() < job.getPickQty() && resQty > 0) {
			// 2.1. 작업 정보 업데이트 처리
			job.setPickedQty(job.getPickedQty() + resQty);
			job.setPickingQty(0);
			boolean isFinished = job.getPickedQty() >= job.getPickQty();
			job.setStatus(isFinished ?  LogisConstants.JOB_STATUS_FINISH : LogisConstants.JOB_STATUS_PICKING);
			job.setPickEndedAt(isFinished ? DateUtil.currentTimeStr() : null);
			this.queryManager.update(job, "pickingQty", "pickedQty", "status", "pickEndedAt", "updatedAt");
			
			// 2.2. 주문 정보 업데이트 처리
			this.updateOrderPickedQtyByConfirm(job, resQty);
		}
		
		// 3. 커스텀 서비스 - 피킹 완료 후 처리 액션
		this.customService.doCustomService(batch.getDomainId(), DIY_PICKED_ACTION, ValueUtil.newMap("batch,job", batch, job));
		
		// 4. 태블릿 등 모바일 장비에서 작업 완료 처리시 표시기 소등 처리
		if(ValueUtil.isNotEqual(exeEvent.getClassifyDevice(), Indicator.class.getSimpleName())) {
			String pickQtyStr = this.toIndicatorStr(resQty);
			this.serviceDispatcher.getIndicationService(job).displayForString(batch.getDomainId(), batch.getId(), batch.getStageCd(), batch.getJobType(), job.getIndCd(), pickQtyStr);
		}
		
		// 5. 릴레이 처리
		WorkCell wc = exeEvent.getWorkCell();
		wc.setLastJobCd("pick");
		wc.setLastPickedQty(resQty);
		this.doNextJob(exeEvent, batch, job, wc, false);
	}

	@Override
	public void cancelAssort(IClassifyRunEvent exeEvent) {
		// 1. 이벤트로 부터 작업에 필요한 데이터 추출
		JobBatch batch = exeEvent.getJobBatch();
		JobInstance job = exeEvent.getJobInstance();
		
		// 2. 작업이 진행 상태이면 취소 처리
		if(job.isTodoJob() && job.getPickingQty() > 0) {
			job.setPickingQty(0);
			// 작업을 '취소 상태'로 변경
			// BatchJobConfigUtil.isPickCancelStatusEnabled(batch) ? LogisConstants.JOB_STATUS_CANCEL : LogisConstants.JOB_STATUS_WAIT;
			job.setStatus(LogisConstants.JOB_STATUS_CANCEL);
			this.queryManager.update(job, "status", "pickingQty", "updatedAt");
		}
		
		// 3. 커스텀 서비스 - 피킹 취소 후 처리 액션
		this.customService.doCustomService(batch.getDomainId(), DIY_CANCEL_PICK_ACTION, ValueUtil.newMap("batch,job", batch, job));
		
		// 4. 태블릿 등 모바일 장비에서 작업 완료 처리시 표시기 소등 처리
		if(ValueUtil.isNotEqual(exeEvent.getClassifyDevice(), Indicator.class.getSimpleName())) {
			String pickQtyStr = this.toIndicatorStr(0);
			this.serviceDispatcher.getIndicationService(job).displayForString(batch.getDomainId(), batch.getId(), batch.getStageCd(), batch.getJobType(), job.getIndCd(), pickQtyStr);
		}
		
		// 5. 릴레이 처리
		WorkCell wc = exeEvent.getWorkCell();
		wc.setLastJobCd("cancel");
		wc.setLastPickedQty(0);
		this.doNextJob(exeEvent, batch, job, wc, false);
	}

	@Override
	public int splitAssort(IClassifyRunEvent exeEvent) {
		// 1. 이벤트에서 수량 조절을 위한 데이터 추출 
		JobInstance job = exeEvent.getJobInstance();
		WorkCell workCell = exeEvent.getWorkCell();
		int resQty = exeEvent.getResQty();
		
		// 2. 수량 조절 처리 
		if(resQty > 0 && job.isTodoJob()) {
			job = this.splitJob(job, workCell, resQty);
		}
		
		// 3. 남은 수량 표시기 점등
		this.serviceDispatcher.getIndicationService(job).indicatorsOn(exeEvent.getJobBatch(), false, ValueUtil.toList(job));
		// 4. 분류 완료 이벤트 전송
		this.eventPublisher.publishEvent(new ClassifyEndEvent(exeEvent));
		// 5. 조정 수량 리턴 
		return resQty;
	}

	@Override
	public int undoAssort(IClassifyRunEvent exeEvent) {
		// 1. 작업 데이터 확정 수량 0으로 업데이트 
		JobBatch batch = exeEvent.getJobBatch();
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
		WorkCell wc = exeEvent.getWorkCell();
		wc.setLastJobCd("undo");
		wc.setLastPickedQty(-1 * pickedQty);
		this.doNextJob(exeEvent, batch, job, wc, false);

		// 4. 주문 취소된 확정 수량 리턴
		return pickedQty;
	}

	@Override
	public BoxPack fullBoxing(IClassifyOutEvent outEvent) {
		// 1. 풀 박스 처리해야 할 작업 리스트 조회
		JobBatch batch = outEvent.getJobBatch();
		
		// 2. 설정에 따라 Fullbox를 지원할 지 여부 판단하여 지원 안 하는 경우 스킵
		String settingKey = outEvent.getStageCd() + ".das.fullbox.support";
		boolean dasFullboxSupport = ValueUtil.toBoolean(SettingUtil.getValue(settingKey, "false"));
		if(!dasFullboxSupport) return null;
		
		// 3. 작업 셀 추출
		WorkCell workCell = outEvent.getWorkCell();
		
		// 4. 작업 배치가 null이면 조회
		if(batch == null) {
			String sql = "select * from job_batches where domain_id = :domainId and status = 'RUN' and equip_cd in (select distinct equip_cd from cells where domain_id = :domainId and cell_cd = :cellCd)";
			Map<String, Object> bParams = ValueUtil.newMap("domainId,cellCd", outEvent.getDomainId(), workCell.getCellCd());
			batch = this.queryManager.selectBySql(sql, bParams, JobBatch.class);
		}
		
		// 5. WorkCell의 셀 번호로 부터 완료된 작업 리스트 조회
		Map<String, Object> params = ValueUtil.newMap("subEquipCd,status", workCell.getCellCd(), LogisConstants.JOB_STATUS_FINISH);
		List<JobInstance> jobList = this.serviceDispatcher.getJobStatusService(batch).searchJobList(batch, params);
		
		// 6. 풀 박스 체크
		if(ValueUtil.isEmpty(jobList)) {
			// 이미 처리된 항목입니다. --> 작업[job.getId()]은 이미 풀 박스가 완료되었습니다.
			String msg = MessageUtil.getMessage("ALREADY_BEEN_PROCEEDED", "Already been proceeded.");
			throw new ElidomRuntimeException(msg);
		}

		// 7. 풀 박스 처리
		BoxPack boxPack = this.boxService.fullBoxing(batch, workCell, jobList, this);
		
		// 8. 다음 작업 처리
		if(boxPack != null) {
			// 다음 작업 처리
			workCell.setLastJobCd("fullbox");
			workCell.setLastPickedQty(0);
			this.doNextJob(outEvent, batch, jobList.get(0), workCell, true);
			outEvent.setResult(boxPack);
		}
		
		outEvent.setExecuted(true);
		// 9. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack partialFullboxing(IClassifyOutEvent outEvent) {
		throw ThrowUtil.newNotSupportedMethod();
	}

	@Override
	public BoxPack cancelBoxing(Long domainId, BoxPack box) {
		// 1. 풀 박스 취소 전 처리
		if(box == null) {
			// 셀에 박싱 처리할 작업이 없습니다 --> 박싱 취소할 박스가 없습니다.
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_FOR_BOXING");
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
			String msg = MessageUtil.getMessage("SPLIT_QTY_LARGER_THAN_PLANNED_QTY", "예정수량보다 분할수량이 커서 작업분할 처리를 할 수 없습니다");
			throw new ElidomRuntimeException(msg);
		}
		
		// 2. 기존 작업 데이터 복사
		JobInstance splittedJob = AnyValueUtil.populate(job, new JobInstance());
		String nowStr = DateUtil.currentTimeStr();
		
		// 3. 분할 작업 데이터를 완료 처리
		splittedJob.setId(AnyValueUtil.newUuid36());
		splittedJob.setPickQty(splitQty);
		splittedJob.setPickingQty(0);
		splittedJob.setPickedQty(splitQty);
		splittedJob.setPickEndedAt(nowStr);
		splittedJob.setStatus(LogisConstants.JOB_STATUS_FINISH);
		this.queryManager.insert(splittedJob);
		
		// 4. 분할 처리된 주문 정보를 업데이트
		this.updateOrderPickedQtyByConfirm(splittedJob, splitQty);
		 
		// 5. 기존 작업 데이터의 수량을 분할 처리 후 남은 수량으로 하고 상태는 '피킹 시작' 처리 
		job.setPickQty(job.getPickQty() - splitQty);
		job.setPickingQty(job.getPickQty());
		job.setPickedQty(0);
		job.setStatus(LogisConstants.JOB_STATUS_PICKING);
		job.setPickStartedAt(nowStr);
		this.queryManager.update(job, "pickQty", "pickingQty", "pickedQty", "status", "pickStartedAt", "updatedAt");
		
		// 6. 기존 작업 데이터 리턴
		return job;
	}
	
	@Override
	public JobInstance findLatestJobForBoxing(Long domainId, String batchId, String cellCd) {
		// 박싱 처리를 위해 로케이션에 존재하는 박스 처리할 작업을 조회
		String sql = "select * from (select * from job_instances where domain_id = :domainId and batch_id = :batchId and sub_equip_cd = :cellCd and status in (:statuses) order by pick_ended_at desc) where rownum <= 1";
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,cellCd,statuses", domainId, batchId, cellCd, LogisConstants.JOB_STATUS_PF);
		return this.queryManager.selectBySql(sql, params, JobInstance.class);
	}
	
	@Override
	public boolean checkStationJobsEnd(JobInstance job, String stationCd) {
		// 릴레이 처리를 위해 작업 스테이션에서 작업이 끝났는지 체크 ...
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,stationCd,inputSeq,statuses", job.getDomainId(), job.getBatchId(), stationCd, job.getInputSeq(), LogisConstants.JOB_STATUS_WIP);
		return this.queryManager.selectSizeBySql(sql, params) == 0;
	}
	
	/**
	 * 상품 투입이 완료 처리 되었는지 체크
	 * 
	 * @param job
	 * @return
	 */
	private boolean checkSkuInputEnd(JobInstance job) {
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,inputSeq,statuses", job.getDomainId(), job.getBatchId(), job.getInputSeq(), LogisConstants.JOB_STATUS_WIP);
		return this.queryManager.selectSizeBySql(sql, params) == 0;
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
	public boolean checkEndClassifyAll(JobBatch batch) {
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("statuses", SysConstants.IN, LogisConstants.JOB_STATUS_WIPC);
		return this.queryManager.selectSize(JobInstance.class, condition) == 0;
	}

	@Override
	public boolean finishAssortCell(JobInstance job, WorkCell workCell, boolean finalEndFlag) {
		// 1. 로케이션에 완료 상태 기록
		String cellJobStatus = finalEndFlag ? LogisConstants.CELL_JOB_STATUS_ENDED : LogisConstants.CELL_JOB_STATUS_ENDING;
		workCell.setStatus(cellJobStatus);
		workCell.setJobInstanceId(finalEndFlag ? null : job.getId());
		this.queryManager.update(workCell, "status", "jobInstanceId", "indCd", "updatedAt");
		
		// 2. 커스텀 서비스 호출 - 셀 주문 완료 후 처리
		this.customService.doCustomService(job.getDomainId(), DIY_CELL_END_ACTION, ValueUtil.newMap("job,workCell", job, workCell));
		
		// 3. 표시기에 분류 처리 내용 표시
		this.serviceDispatcher.getIndicationService(job).indicatorOnForPickEnd(job, finalEndFlag);
		return true;
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
			Cell c = ValueUtil.isNotEmpty(cellCd) ? 
				AnyEntityUtil.findEntityBy(errorEvent.getDomainId(), false, Cell.class, "stationCd", "cellCd", cellCd) : null;
			
			String errMsg = (th.getCause() == null) ? th.getMessage() : th.getCause().getMessage();
			JobBatch batch = errorEvent.getJobBatch();
			String[] deviceTypeList = (isIndicatorDevice) ? DasBatchJobConfigUtil.getDeviceList(batch) : new String[] { device };
			
			if(deviceTypeList != null) {
				for(String deviceType : deviceTypeList) {
					DeviceEvent event = new DeviceEvent(batch.getDomainId(), deviceType, batch.getStageCd(), batch.getEquipType(), batch.getEquipCd(), (c != null ? c.getStationCd() : null), null, batch.getJobType(), "error", errMsg);
					this.eventPublisher.publishEvent(event);
				}
			}
		}

		// 5. 예외 발생
		throw (th instanceof ElidomException) ? (ElidomException)th : new ElidomRuntimeException(th);
	}
	
	/**
	 * 검수 기능으로 표시기 점등
	 * 
	 * @param batch
	 * @param comCd
	 * @param skuCd
	 * @param inputSeq
	 * @return
	 */
	private List<JobInstance> indOnByInspection(JobBatch batch, String comCd, String skuCd, int inputSeq) {
		// 1. 처리한 작업 리스트 조회 - 상품, 셀 별로 합계해서 표시기 점등
		String sql = this.dasQueryStore.getSearchInspectionJobListQuery();
		Map<String, Object> condition = ValueUtil.newMap("domainId,batchId,comCd,skuCd,inputSeq,statuses", batch.getDomainId(), batch.getId(), comCd, skuCd, inputSeq, LogisConstants.JOB_STATUS_FBER);
		List<JobInstance> doneJobList = this.queryManager.selectListBySql(sql, condition, JobInstance.class, 0, 0);
		
		// 2. 처리할 작업 리스트 조회 - 피킹, 투입, 취소 등 작업 리스트 조회 
		IJobStatusService statusSvc = this.serviceDispatcher.getJobStatusService(batch);
		condition.put("statuses", LogisConstants.JOB_STATUS_WIPC);
		List<JobInstance> todoJobList = statusSvc.searchPickingJobList(batch, condition);
		
		// 3. inspectJobList는 inspection 모드로 표시기 점등
		IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
		
		// 4. 검수 모드로 표시기 점등, 10개씩 쪼개서 점등
		// RuntimeIndServiceUtil.indOnByInspectJobList(batch, doneJobList);
		if(ValueUtil.isNotEmpty(doneJobList)) {
			List<JobInstance> sliceDoneJobList = new ArrayList<JobInstance>();
			for(JobInstance job : doneJobList) {
				sliceDoneJobList.add(job);
				
				if(sliceDoneJobList.size() >= 10) {
					RuntimeIndServiceUtil.indOnByInspectJobList(batch, sliceDoneJobList);
					sliceDoneJobList.clear();
					ThreadUtil.sleep(500);
				}
			}
			
			if(!sliceDoneJobList.isEmpty()) {
				RuntimeIndServiceUtil.indOnByInspectJobList(batch, sliceDoneJobList);
			}
		}
		
		// 5. picking 모드로 표시기 점등
		if(ValueUtil.isNotEmpty(todoJobList)) {
			String currentTimeStr = DateUtil.currentTimeStr();
			for(JobInstance job : todoJobList) {
				job.setPickStartedAt(currentTimeStr);
				job.setPickingQty(job.getPickQty());
			}
			indSvc.indicatorsOn(batch, false, todoJobList);
		}
		
		// 6. PDA, Tablet, KIOSK 등에 표시할 작업 리스트 리턴
		return todoJobList;
	}

	/**
	 * 상품 투입 전 Validation 체크
	 * 
	 * @param batch
	 * @param comCd
	 * @param skuCd
	 * @return 작업 배치 내 상품 투입 시퀀스 
	 */
	private int validateInputSKU(JobBatch batch, String comCd, String skuCd) {
		// 투입 상품이 현재 작업 배치에 존재하는 상품인지 체크
		Long domainId = batch.getDomainId();
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("comCd", comCd);
		condition.addFilter("skuCd", skuCd);
		condition.addFilter("equipCd", batch.getEquipCd());
		
		if(this.queryManager.selectSize(JobInstance.class, condition) == 0) {
			// 상품 코드 조회, 없으면 상품 코드로 상품을 찾을 수 없습니다.
			AnyEntityUtil.findEntityBy(domainId, true, SKU.class, "comCd,skuCd,skuBarcd", "comCd,skuCd", comCd, skuCd);
			// 스캔한 상품은 현재 작업 배치에 존재하지 않습니다
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_SKU_FOUND_IN_SCOPE", "terms.label.job_batch");
			
		} else {
			// 상품의 투입 시퀀스를 조회 
			return this.findSkuInputSeq(batch, comCd, skuCd);
		}
	}

	/**
	 * 상품 코드로 투입 시퀀스를 조회
	 * 
	 * @param batch
	 * @param comCd
	 * @param skuCd
	 * @return
	 */
	private int findSkuInputSeq(JobBatch batch, String comCd, String skuCd) {
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,equipCd,comCd,skuCd", batch.getDomainId(), batch.getId(), batch.getEquipCd(), comCd, skuCd);
		String sql = this.dasQueryStore.getDasFindInputSeqBySkuQuery();
		int inputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
		return inputSeq > 0 ? inputSeq : -1;
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
		// 1. 작업 리스트로 부터 매장 개수 구하기 
		Long classCnt = jobList.stream().map(job -> job.getClassCd()).distinct().count();
		
		// 2. 투입 정보 생성
		JobInstance firstJob = jobList.get(0);
		int nextInputSeq = this.serviceDispatcher.getJobStatusService(batch).findNextInputSeq(batch);
		JobInput newInput = new JobInput();
		newInput.setDomainId(batch.getDomainId());
		newInput.setBatchId(batch.getId());
		newInput.setEquipType(LogisConstants.EQUIP_TYPE_RACK);
		newInput.setEquipCd(batch.getEquipCd());
		newInput.setStationCd(firstJob.getStationCd());
		newInput.setInputSeq(nextInputSeq);
		newInput.setComCd(comCd);
		newInput.setSkuCd(skuCd);

		// 3. 투입 유형 설정 - TODO 투입 유형을 설정에서 조회해서 혹은 화면에서 직접 넘겨주기 ...
		if(DasBatchJobConfigUtil.isSingleSkuInputEnabled(batch)) {
			newInput.setInputType(LogisCodeConstants.JOB_INPUT_TYPE_PCS);
		} else if(DasBatchJobConfigUtil.isSingleBoxInputEnabled(batch)) {
			newInput.setInputType(LogisCodeConstants.JOB_INPUT_TYPE_BOX);
		} else if(DasBatchJobConfigUtil.isBundleInputEnabled(batch)) {
			newInput.setInputType(LogisCodeConstants.JOB_INPUT_TYPE_BUNDLE);
		} else {
			newInput.setInputType(LogisCodeConstants.JOB_INPUT_TYPE_PCS);
		}
		
		newInput.setInputQty(classCnt.intValue());
		newInput.setStatus(JobInput.INPUT_STATUS_WAIT);
		
		// 4. 이전 투입에 대한 컬러 조회
		IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
		firstJob.setInputSeq(nextInputSeq);
		String prevColor = indSvc.prevIndicatorColor(firstJob);
		String currentColor = indSvc.nextIndicatorColor(firstJob, prevColor);
		newInput.setColorCd(currentColor);
		this.queryManager.insert(newInput);
		
		// 5. 투입 작업 리스트 업데이트 
		String currentTime = DateUtil.currentTimeStr();
		for(JobInstance job : jobList) {
			job.setStatus(LogisConstants.JOB_STATUS_INPUT);
			job.setInputSeq(nextInputSeq);
			job.setColorCd(currentColor);
			job.setInputAt(currentTime);
			job.setPickStartedAt(currentTime);
			job.setPickingQty(job.getPickQty());
		}
		
		// 6. 작업 정보 업데이트
		this.queryManager.updateBatch(jobList, "status", "pickingQty", "colorCd", "inputSeq", "pickStartedAt", "inputAt", "updaterId", "updatedAt");
		
		// 5. 투입 정보 리턴
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
		// 1. 배치 호기별로 표시기 점등이 안 되어 있는 존을 조회하여 해당 존의 표시기 리스트를 점등한다.
		List<String> stationList = AnyValueUtil.filterValueListBy(jobList, "stationCd");
		
		// 2. 작업 배치 내 작업 존 리스트 내에 피킹 (표시기 점등) 중인 작업이 있는 작업 존 리스트를 조회
		String sql = this.dasQueryStore.getDasSearchWorkingStationQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,inputSeq,status,stationList", input.getDomainId(), input.getBatchId(), input.getInputSeq(), LogisConstants.JOB_STATUS_PICKING, stationList);
		List<String> pickingStationList = this.queryManager.selectListBySql(sql, params, String.class, 0, 0);
		
		// 3. 피킹된 (즉 작업 중인) 작업 존 외에 존재하는 작업 리스트를 대상으로 표시기 점등
		List<JobInstance> indJobList = jobList.stream().filter(job -> (!pickingStationList.contains(job.getStationCd()))).collect(Collectors.toList());
		
		// 4. 커스텀 서비스 호출 - 상품 투입 후 액션
		this.customService.doCustomService(batch.getDomainId(), DIY_INPUT_SKU_ACTION, ValueUtil.newMap("batch,jobList", batch, jobList));
		
		// 5. 10개 이하인 경우 한꺼번에 점등
		if(indJobList.size() <= 10) {
			this.serviceDispatcher.getIndicationService(batch).indicatorsOn(batch, false, indJobList);
			
		// 7. 10개 이상인 경우 10개씩 쪼개서 점등
		} else {
			IIndicationService indSvc = this.serviceDispatcher.getIndicationService(batch);
			List<JobInstance> sliceIndJobList = new ArrayList<JobInstance>();
			
			for(JobInstance job : indJobList) {
				sliceIndJobList.add(job);
				
				if(sliceIndJobList.size() >= 10) {
					indSvc.indicatorsOn(batch, false, sliceIndJobList);
					ThreadUtil.sleep(500);
					sliceIndJobList.clear();
				}
			}
			
			if(!sliceIndJobList.isEmpty()) {
				indSvc.indicatorsOn(batch, false, sliceIndJobList);
			}
		}
	}

	/**
	 * 다음 작업 처리
	 * 
	 * @param classifyEvent
	 * @param batch
	 * @param job
	 * @param cell
	 * @param fullboxAction
	 */
	private void doNextJob(IClassifyEvent classifyEvent, JobBatch batch, JobInstance job, WorkCell cell, boolean fullboxAction) {
		// 1. WorkCell 업데이트
		cell.setJobInstanceId(job != null ? job.getId() : null);
		cell.setIndCd(job.getIndCd());
		
		if(fullboxAction && (ValueUtil.isEqualIgnoreCase(cell.getStatus(), LogisConstants.CELL_JOB_STATUS_ENDING) || ValueUtil.isEqualIgnoreCase(cell.getStatus(), LogisConstants.CELL_JOB_STATUS_ENDED))) {
			// 2. 풀 박스 액션이고 셀 상태가 ENDED, ENDING인 경우 ...
			this.finishAssortCell(job, cell, true);
			
		} else {
			// 3. 현재 작업 중인 투입 시퀀스에 대해서 분류가 완료 되었는지 여부
			boolean currentStationEnded = this.checkStationJobsEnd(job, job.getStationCd());
			// 3.1 작업 스테이션이 완료되었다면
			if(currentStationEnded) {
				// 3.2 현재 투입 정보가 완료되었는지 체크
				if(this.checkSkuInputEnd(job)) {
					// 현재 투입 정보를 완료 처리
					String query = "update job_inputs set status = :status where domain_id = :domainId and batch_id = :batchId and input_seq = :inputSeq";
					Map<String, Object> condition = ValueUtil.newMap("domainId,batchId,inputSeq,status", job.getDomainId(), batch.getId(), job.getInputSeq(), JobInput.INPUT_STATUS_FINISHED);
					this.queryManager.executeBySql(query, condition);
				}
				
				// 3.3 릴레이 처리...
				this.relayLightOn(batch, job, job.getStationCd());
			}
			
			// 4. 모든 셀의 분류가 완료되었는지 체크
			boolean cellEndFlag = this.checkCellAssortEnd(job, false);
			// 4.1 해당 셀의 작업이 모두 완료 상태인지 체크
			if(cellEndFlag) {
				// WorkCell 상태 완료 처리
				this.finishAssortCell(job, cell, false);
			// 4.2 다음 순서에 투입된 표시기 릴레이로 점등
			} else {
				// WorkCell 업데이트
				this.queryManager.update(cell, "jobInstanceId", "indCd", "lastJobCd", "lastPickedQty", "updatedAt");
			}
		}
		
		// 5. 릴레이 처리 후 모바일 장비 리프레쉬 메시지 전달
		this.eventPublisher.publishEvent(new ClassifyEndEvent(classifyEvent));
	}
	
	/**
	 * 현재 작업 투입 순서 이후의 투입 작업 중에 현재 작업 존과 같은 곳에 작업 리스트 조회
	 * 
	 * @param batch
	 * @param job
	 * @param stationCd
	 */
	private void relayLightOn(JobBatch batch, JobInstance job, String stationCd) {
		// 1. 릴레이 처리를 위한 다음 처리할 InputSeq를 조회
		String sql = this.indQueryStore.getNextRelayInputSeqQuery();
		Long domainId = batch.getDomainId();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,inputSeq,statuses,equipCd,stationCd", domainId, batch.getId(), job.getInputSeq(), LogisConstants.JOB_STATUS_IP, batch.getEquipCd(), stationCd);
		int nextInputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
		if(nextInputSeq <= job.getInputSeq()) {
			return;
		}
		
		// 2. 현재 작업 투입 순서 이후의 투입 작업 중에 현재 작업 존과 같은 곳에 작업 리스트 조회
		params = ValueUtil.newMap("stationCd,status,inputSeq", stationCd, LogisConstants.JOB_STATUS_INPUT, nextInputSeq);
		List<JobInstance> relayJobs = this.serviceDispatcher.getJobStatusService(batch).searchPickingJobList(batch, params);
		
		// 3. 릴레이 처리 작업 리스트가 존재하면
		if(ValueUtil.isNotEmpty(relayJobs)) {
			// 3.1. 릴레이 작업에 포함된 작업에 소속된 투입 정보의 상태를 '진행 중'으로 변경
			JobInstance relayJob = relayJobs.get(0);
			params = ValueUtil.newMap("domainId,batchId,inputSeq,status,prevStatus", domainId, batch.getId(), relayJob.getInputSeq(), JobInput.INPUT_STATUS_RUNNING, JobInput.INPUT_STATUS_WAIT);
			String query = "update job_inputs set status = :status where domain_id = :domainId and batch_id = :batchId and input_seq = :inputSeq and status = :prevStatus";
			this.queryManager.executeBySql(query, params);
			// 3.2 작업 리스트에서 피킹 중 수량 정보 업데이트 ...
			relayJobs.stream().forEach(j -> { j.setPickingQty(j.getPickQty()); });
			// 3.3 작업 리스트로 표시기 점등
			this.serviceDispatcher.getIndicationService(job).indicatorsOn(batch, false, relayJobs);
		}
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
		condition.addFilter("batchId", job.getBatchId());
		condition.addFilter("subEquipCd", job.getSubEquipCd());
		condition.addFilter("skuCd", job.getSkuCd());
		condition.addFilter("status", SysConstants.IN, ValueUtil.toList(LogisConstants.COMMON_STATUS_RUNNING, LogisConstants.COMMON_STATUS_WAIT));
		condition.addOrder("orderNo", true);
		condition.addOrder("pickedQty", false);
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
	 * 표시기에 표시할 수량을 문자로 표현 
	 * 
	 * @param qty
	 * @return
	 */
	private String toIndicatorStr(int qty) {
		return StringUtils.leftPad(ValueUtil.toString(qty), 6);
	}
}
