package operato.logis.gtp.base.service.rtn;
     
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.ICategorizeEvent;
import xyz.anythings.base.event.IClassifyErrorEvent;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.IClassifyOutEvent;
import xyz.anythings.base.event.IClassifyRunEvent;
import xyz.anythings.base.event.classfy.ClassifyRunEvent;
import xyz.anythings.base.model.Category;
import xyz.anythings.base.service.api.IAssortService;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.impl.LogisServiceDispatcher;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.anythings.sys.util.AnyValueUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.util.ValueUtil; 



/**
 * 반품 분류 서비스  구현
 *
 * @author shortstop
 */
@Component("rtnAssortService")
public class RtnAssortService extends AbstractExecutionService implements IAssortService {

	/**
	 * 서비스 디스패처
	 */
	@Autowired
	private LogisServiceDispatcher serviceDispatcher;
	
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
	public JobConfigSet getJobConfigSet(String batchId) {
		return this.serviceDispatcher.getConfigSetService().getConfigSet(batchId);
	}
	
	@Override
	public Object boxCellMapping(JobBatch batch, String cellCd, String boxId) {
		return this.boxService.assignBoxToCell(batch, cellCd, boxId);
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
			return null;
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
		
		switch(classifyAction) {
			//분류 처리 액션 - 확정 처리
			case LogisCodeConstants.CLASSIFICATION_ACTION_CONFIRM :
				this.confirmAssort(exeEvent);
				break; 
			// Indicator의 Cancel 기능 버튼을 눌러서 처리
			case LogisCodeConstants.CLASSIFICATION_ACTION_CANCEL :
				this.cancelAssort(exeEvent);
				break; 
			//분류 처리 액션 - 수정 처리 
			case LogisCodeConstants.CLASSIFICATION_ACTION_MODIFY :
				this.splitAssort(exeEvent);
				break;
			//분류 처리 액션 - Fullbox
			case LogisCodeConstants.CLASSIFICATION_ACTION_FULL :
				this.fullBoxing(exeEvent);
				break;
		}
		
		exeEvent.setExecuted(true);
		return job;
	}
	 
	@Override
	public Object output(IClassifyOutEvent outputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkEndClassifyAll(String batchId) {
		// TODO Auto-generated method stub
		return false;
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
	public void handleClassifyException(IClassifyErrorEvent errorEvent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IBoxingService getBoxingService(Object... params) {
		return this.boxService;
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
			throw new ElidomRuntimeException("투입된 이후 확정 처리를 안 한 셀이 있습니다.");
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
		// TODO
		return null;
	}

	@Override
	public void confirmAssort(IClassifyRunEvent exeEvent) { 
		JobInstance job = exeEvent.getJobInstance(); 
		int resQty = job.getPickingQty(); // 확정 수량  
		
		job.setPickedQty(job.getPickedQty() + resQty);
		job.setPickingQty(0);
		
		// 1. 작업 정보 - 확정 업데이트
		if(job.getPickedQty() >= job.getPickQty()) {
			job.setStatus(LogisConstants.JOB_STATUS_FINISH);
		} 
		
		this.queryManager.update(job, "pickingQty", "pickedQty", "status", "updatedAt");
		
		// 2. 주문 정보 업데이트 처리
		this.updateOrderPickedQtyByConfirm(job, resQty);
	}

	@Override
	public void cancelAssort(IClassifyRunEvent exeEvent) { 
		JobInstance job = exeEvent.getJobInstance();
		job.setPickingQty(0);		
		this.queryManager.update(job, "pickingQty", "updatedAt");
	}

	@Override
	public int splitAssort(IClassifyRunEvent exeEvent) { 
		JobInstance job = exeEvent.getJobInstance();
		int resQty = exeEvent.getResQty(); // 확정 수량 
		int pickedQty = job.getPickedQty() + resQty;
		
		// 1. 작업 정보 - 확정 수량 변경 업데이트
		job.setPickingQty(0);
		job.setPickedQty(pickedQty);
		
		if(job.getPickedQty() >= job.getPickQty()) { 
			job.setStatus(LogisConstants.JOB_STATUS_FINISH);
		}
		
		this.queryManager.update(job, "pickingQty", "pickedQty", "status", "updatedAt");
		
		// 2. 주문 정보 업데이트 처리
		this.updateOrderPickedQtyByConfirm(job, resQty);
		
		// 3. 조정 수량 리턴 
		return resQty;
	}

	@Override
	public int undoAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BoxPack fullBoxing(IClassifyRunEvent exeEvent) {
		// TODO boxId가 넘어와야 함 ...
		JobInstance job = exeEvent.getJobInstance();
		// 풀 박스시는 WorkCell에 Locking
		WorkCell workCell = AnyEntityUtil.findEntityBy(job.getDomainId(), true, true, WorkCell.class, "id,cell_cd,ind_cd,job_type,com_cd,sku_cd,box_id,status,job_instance_id", "domainId,batchId,cellCd", job.getDomainId(), job.getBatchId(), job.getSubEquipCd());
		return this.boxService.fullBoxing(exeEvent.getJobBatch(), workCell, ValueUtil.toList(job), this);
	}

	@Override
	public BoxPack partialFullboxing(IClassifyRunEvent exeEvent) {
		// TODO boxId가 넘어와야 함 ...
		JobInstance job = exeEvent.getJobInstance();
		int resQty = exeEvent.getReqQty();
		// 풀 박스시는 WorkCell에 Locking
		WorkCell workCell = AnyEntityUtil.findEntityBy(job.getDomainId(), true, true, WorkCell.class, "id,cell_cd,ind_cd,job_type,com_cd,sku_cd,box_id,status,job_instance_id", "domainId,batchId,cellCd", job.getDomainId(), job.getBatchId(), job.getSubEquipCd());
		return this.boxService.partialFullboxing(exeEvent.getJobBatch(), workCell, ValueUtil.toList(job), resQty, this);
	}

	@Override
	public BoxPack cancelBoxing(Long domainId, String boxPackId) {
		BoxPack boxPack = AnyEntityUtil.findEntityById(true, BoxPack.class, boxPackId);
		return this.boxService.cancelFullboxing(boxPack);
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

	@Override
	public boolean checkStationJobsEnd(JobBatch batch, String stationCd, JobInstance job) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean checkCellAssortEnd(JobBatch batch, String stationCd, JobInstance job) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean finishAssortCell(JobInstance job, WorkCell workCell, boolean finalEndFlag) {
		// TODO Auto-generated method stub
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
		condition.addFilter("batchId",			job.getBatchId()); 
		condition.addFilter("skuCd",			job.getSkuCd());
		condition.addFilter("status",	"in",	ValueUtil.toList(LogisConstants.COMMON_STATUS_RUNNING, LogisConstants.COMMON_STATUS_WAIT));
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

}
