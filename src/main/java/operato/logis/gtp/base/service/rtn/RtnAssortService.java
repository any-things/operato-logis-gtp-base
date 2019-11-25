package operato.logis.gtp.base.service.rtn;
     
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;  
import org.springframework.stereotype.Component;
 
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet; 
import xyz.anythings.base.entity.JobInstance;
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
import xyz.anythings.sys.service.AbstractExecutionService; 
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException; 
import xyz.elidom.util.DateUtil;
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
	
	@Override
	public String getJobType() {
		return LogisConstants.JOB_TYPE_RTN;
	}

	@Override
	public JobConfigSet getJobConfigSet(String batchId) {
		return this.serviceDispatcher.getConfigSetService().getConfigSet(batchId);
	}

	@Override
	public Category categorize(ICategorizeEvent event) {
		// TODO 중분류 
		return null;
	}

	@Override
	public String checkInput(Long domainId, String inputId, Object... params) {
		// TODO inputId를 체크하여 어떤 코드 인지 (상품 코드, 상품 바코드, 박스 ID, 랙 코드, 셀 코드 등) 체크 
		return null;
	}

	@Override
	public Object input(IClassifyInEvent inputEvent) {
		// TODO 소분류 - 상품 투입 
		return this.inputSkuSingle(inputEvent); 
		
	} 
	
	@EventListener(classes = ClassifyRunEvent.class, condition = "#event.jobType == 'rtn")
	public Object classify(IClassifyRunEvent exeEvent) { 
		String classifyAction = exeEvent.getClassifyAction();
		JobInstance instance = exeEvent.getJobInstance(); 
		
		Integer reqQty = exeEvent.getReqQty();//피킹 수량
		Integer resQty = exeEvent.getResQty();//확정 수량 
		Integer picking = 0, picked=0;
		
		switch(classifyAction) {
			case LogisCodeConstants.CLASSIFICATION_ACTION_CONFIRM :
				this.confirmAssort(exeEvent);
			break; 
			case LogisCodeConstants.CLASSIFICATION_ACTION_CANCEL :
				this.cancelAssort(exeEvent);
				break; 
			case LogisCodeConstants.CLASSIFICATION_ACTION_MODIFY :
				this.splitAssort(exeEvent);
				break;
			case LogisCodeConstants.CLASSIFICATION_ACTION_FULL :
				this.fullBoxing(exeEvent);
				break;
				
			default :
				break;
		}
		return null;
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
		// TODO 배치 마감 시점에 반품 관련 추가 처리 - Ex) 셀 별 잔량 풀 박스 처리 ... 
		
	}

	@Override
	public void handleClassifyException(IClassifyErrorEvent errorEvent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public IBoxingService getBoxingService(Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object inputSkuSingle(IClassifyInEvent inputEvent) {
		JobBatch batch = inputEvent.getJobBatch(); 
		String comCd = inputEvent.getComCd();
		String skuCd = inputEvent.getInputCode();
		Integer inputQty = inputEvent.getInputQty();
		String nowStr = DateUtil.currentTimeStr();
		
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("comCd", comCd);
		condition.addFilter("skuCd", skuCd);
		condition.addFilter("equipCd", batch.getEquipCd());
		condition.addFilter("status", "in", LogisConstants.JOB_STATUS_WIPC);
		JobInstance instance = this.queryManager.selectByCondition(JobInstance.class, condition);
		
		Integer pickingQty = instance.getPickingQty()+inputQty;
	 
		if(instance.getPickQty() > pickingQty)
		{	
			//배치의 Max Seq 사용
			instance.setInputSeq(1);
			instance.setPickingQty(pickingQty);
			instance.setStatus(LogisConstants.JOB_STATUS_PICKING);
			if(ValueUtil.isEmpty(instance.getPickStartedAt()))
			{
				instance.setPickStartedAt(nowStr);
			}
			
			if(ValueUtil.isEmpty(instance.getInputAt()))
			{
				instance.setInputAt(nowStr);
			} 
			instance.setColorCd(LogisConstants.COLOR_RED);
			this.queryManager.update(instance, "inputSeq", "pickingQty", "status", "pickStartedAt", "inputAt", "colorCd");
			
			// 표시기 점등
			this.serviceDispatcher.getIndicationService(batch).indicatorOnForPick(instance, instance.getPickingQty(), 0, 0);
			
		}else {
			// 처리 가능한 수량 초과.
			throw new ElidomRuntimeException("처리 예정 수량을 초과 했습니다.");
		} 
		
		return instance;
	}

	@Override
	public Object inputSkuBundle(IClassifyInEvent inputEvent) {
		// TODO 상품 묶음 투입
		return null;
	}

	@Override
	public Object inputSkuBox(IClassifyInEvent inputEvent) {
		// TODO 상품을 박스 단위 투입
		return null;
	}

	@Override
	public Object inputForInspection(IClassifyInEvent inputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void confirmAssort(IClassifyRunEvent exeEvent) {
		int reqQty = exeEvent.getReqQty(); //불켜진 수량
		int resQty = exeEvent.getResQty(); // 확정 수량
		
		JobInstance job = exeEvent.getJobInstance();
		job.setPickingQty(0);
		job.setPickedQty(job.getPickedQty() + resQty);
		
		if(job.getPickedQty() >= job.getPickQty()) {
			job.setStatus(LogisConstants.JOB_STATUS_FINISH);
			this.queryManager.update(job, "pickingQty", "pickedQty","status");
		}else {
			this.queryManager.update(job, "pickingQty", "pickedQty");
		} 
	}

	@Override
	public void cancelAssort(IClassifyRunEvent exeEvent) {
		int reqQty = exeEvent.getReqQty(); //불켜진 수량
		int resQty = exeEvent.getResQty(); // 확정 수량
		JobInstance job = exeEvent.getJobInstance();
		
		job.setPickingQty(0);
		
		this.queryManager.update(job, "pickingQty");
	}

	@Override
	public int splitAssort(IClassifyRunEvent exeEvent) {
		int reqQty = exeEvent.getReqQty(); //불켜진 수량
		int resQty = exeEvent.getResQty(); // 확정 수량
		
		JobInstance job = exeEvent.getJobInstance();
		job.setPickingQty(0);
		job.setPickedQty(job.getPickedQty() + resQty);
		
		if(job.getPickedQty() >= job.getPickQty()) {
			job.setStatus(LogisConstants.JOB_STATUS_FINISH);
			this.queryManager.update(job, "pickingQty", "pickedQty","status");
		}else {
			this.queryManager.update(job, "pickingQty", "pickedQty");
		} 
		return 0;
	}

	@Override
	public int undoAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BoxPack fullBoxing(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack partialFullboxing(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack cancelBoxing(Long domainId, String boxPackId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JobInstance splitJob(JobInstance job, WorkCell workCell, int splitQty) {
		// TODO Auto-generated method stub
		return null;
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
	
	
	
}
