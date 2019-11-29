package operato.logis.gtp.base.service.rtn;
     
import java.util.ArrayList;
import java.util.Date;
import java.util.List; 

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
 
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxItem;
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
import xyz.anythings.sys.service.AbstractExecutionService; 
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
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		//1. Box 사용여부 체크
		condition.addFilter("boxTypeCd", LogisConstants.BOX_ID_UNIQUE_SCOPE_GLOBAL);
		condition.addFilter("boxId",boxId); 
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
		
	
		if(boxPack == null) {
			throw new ElidomRuntimeException("박스 정보가 없습니다.");
		}else if(boxPack.getStatus()=="W") {
			throw new ElidomRuntimeException("이미 사용중인 박스입니다.");
		} 
		
		condition.removeFilter("boxTypeCd");
		condition.removeFilter("boxId");
		condition.removeFilter("stageCd");
		
		// 2. 작업 WorkCell 조회
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("cellCd", cellCd);
		
		WorkCell cell = this.queryManager.selectByCondition(WorkCell.class, condition);
		cell.setBoxId(boxId);
		
		condition.removeFilter("cellCd");
		
		//3. JobInstance 정보 조회
		condition.addSelect("id","picked_qty","picking_qty");
		condition.addFilter("subEquipCd", cellCd); 
		condition.addFilter("status","in", LogisConstants.JOB_STATUS_PF); 
		JobInstance job = this.queryManager.selectByCondition(JobInstance.class, condition);
		
		if(job.getPickingQty()>0) {
			throw new ElidomRuntimeException("작업 완료를 해주세요.");
		}
		return ValueUtil.newMap("detail,job_id,picked_qty,picking_qty", cell,job.getId(), job.getPickedQty(),job.getPickingQty());
 
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
		condition.addFilter("skuCd", skuCd);
		condition.removeFilter("status");
		condition.addFilter("status", "in", LogisConstants.JOB_STATUS_WIPC);
		condition.removeFilter("pickingQty");
		JobInstance job = this.queryManager.selectByCondition(JobInstance.class, condition);
		Integer pickingQty = job.getPickingQty()+inputQty;
	 
		// 3. 작업 인스턴스 정보 업데이트
		if(job.getPickQty() >= (job.getPickedQty()+pickingQty))
		{	
			//3-1 배치의 Max Seq 사용
			job.setInputSeq(1);
			job.setPickingQty(pickingQty);
			job.setStatus(LogisConstants.JOB_STATUS_PICKING);
			if(ValueUtil.isEmpty(job.getPickStartedAt()))
			{
				job.setPickStartedAt(nowStr);
			}
			
			if(ValueUtil.isEmpty(job.getInputAt()))
			{
				job.setInputAt(nowStr);
			} 
			job.setColorCd(LogisConstants.COLOR_RED);
			job.setUpdatedAt(new Date());
			this.queryManager.update(job, "inputSeq", "pickingQty", "status", "pickStartedAt", "inputAt", "updatedAt", "colorCd");
			
			// 3-2 표시기 점등
			this.serviceDispatcher.getIndicationService(batch).indicatorOnForPick(job, job.getPickingQty(), 0, 0);
			
		} else {
			// 처리 가능한 수량 초과.
			throw new ElidomRuntimeException("처리 예정 수량을 초과 했습니다.");
		} 
		 
		return job;
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
		JobInstance job = exeEvent.getJobInstance(); 
		int resQty = job.getPickingQty(); // 확정 수량  
		
		job.setPickedQty(job.getPickedQty() + resQty);
		job.setPickingQty(0);
		job.setUpdatedAt(new Date());
		
		// 1. 작업 정보 - 확정 업데이트
		if(job.getPickedQty() >= job.getPickQty()) {
			job.setStatus(LogisConstants.JOB_STATUS_FINISH);
			this.queryManager.update(job, "pickingQty", "pickedQty","updatedAt","status");
		}else { 
			this.queryManager.update(job, "pickingQty", "pickedQty","updatedAt");
		} 
		
		// 2. 주문 정보 업데이트 처리
		this.updateOrderByConfirm(job,resQty);
	}

	@Override
	public void cancelAssort(IClassifyRunEvent exeEvent) { 
		JobInstance job = exeEvent.getJobInstance();
		
		job.setPickingQty(0); 
		job.setUpdatedAt(new Date());
		
		this.queryManager.update(job, "pickingQty","updatedAt");
	}

	@Override
	public int splitAssort(IClassifyRunEvent exeEvent) { 
		int resQty = exeEvent.getResQty(); // 확정 수량 
		int pickedQty=0;
		
		JobInstance job = exeEvent.getJobInstance();
		 
		pickedQty = job.getPickedQty();
		pickedQty = pickedQty + resQty;
		
		job.setPickingQty(0);
		job.setPickedQty(pickedQty);
		job.setUpdatedAt(new Date());
		
		// 1. 작업 정보 - 확정 수량 변경 업데이트
		if(job.getPickedQty() >= job.getPickQty()) { 
			job.setStatus(LogisConstants.JOB_STATUS_FINISH);
			this.queryManager.update(job, "pickingQty", "pickedQty", "status","updatedAt");
		}else {
			this.queryManager.update(job,  "pickingQty", "pickedQty","updatedAt" );
		} 
		
		// 2. 주문 정보 업데이트 처리
		this.updateOrderByConfirm(job,resQty);
		
		return 0;
	}

	@Override
	public int undoAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BoxPack fullBoxing(IClassifyRunEvent exeEvent) {
		JobInstance job = exeEvent.getJobInstance(); 
		String nowStr = DateUtil.currentTimeStr(); 
		int resQty = exeEvent.getReqQty(); // 확정 수량 
		
		// 1. BoxPack 정보 조회
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId());
		condition.addFilter("boxId",	job.getBoxId()); 
		condition.addFilter("batchId",	job.getBatchId());  
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
				
		if((job.getPickQty()-resQty)>0 ) {
			// 1. FullBox Split 처리
			job = this.splitJob(job, null, resQty);
		}else if((job.getPickQty()-resQty) <= 0) { 
			//2.1 FullBox 처리
			job.setPickingQty(0);
			job.setPickedQty(resQty); 
			job.setBoxInQty(resQty); 
			job.setBoxedAt(nowStr);
			job.setPickEndedAt(nowStr);
			job.setStatus(LogisConstants.JOB_STATUS_BOXED); 
			job.setUpdatedAt(new Date()); 
			this.queryManager.update(job, "pickingQty","pickedQty","boxId","boxInQty","boxTypeCd","boxedAt", "status","pickEndedAt","updatedAt"); 
		} 
		
		//3.2 BoxItem 생성
		this.generateBoxItemBy(job, boxPack);	 
		
		//3.3 주문 정보 업데이트 처리
		this.updateOrderByFullBox(job,resQty); 
		
		return boxPack;
		
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
		
		JobInstance newJob = AnyValueUtil.populate(job, new JobInstance());
		String newJobId = AnyValueUtil.newUuid36(); 
		String nowStr = DateUtil.currentTimeStr();
		int pickedQty = job.getPickedQty()-splitQty;
		
		//1.1 Split Job 처리
		newJob.setId(newJobId);
		newJob.setPickQty(splitQty);
		newJob.setPickingQty(0);
		newJob.setPickedQty(splitQty);
		newJob.setBoxInQty(splitQty); 
		newJob.setBoxedAt(nowStr);
		newJob.setPickEndedAt(nowStr);
		newJob.setStatus(LogisConstants.JOB_STATUS_BOXED);
		newJob.setCreatedAt(new Date());
		newJob.setUpdatedAt(new Date());
 
		this.queryManager.insert(newJob);
		 
		job.setPickQty(job.getPickQty()-splitQty);
		job.setPickingQty(0);
		job.setPickedQty(pickedQty);
		job.setBoxInQty(0);
		if(pickedQty>0) {
			job.setStatus(LogisConstants.JOB_STATUS_PICKING); 
		}else { 
			job.setStatus(LogisConstants.JOB_STATUS_WAIT); 
		}
		job.setUpdatedAt(new Date());
		
		this.queryManager.update(job, "pickQty","pickingQty","pickedQty", "status","updatedAt");	
		
		return newJob;
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
	 * 작업 정보 기준으로 BoxItem 생성
	 *
	 * @param job
	 * @param boxPack
	 */
	public void generateBoxItemBy(JobInstance job, BoxPack boxPack) {
		// 1. 주문정보 조회
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId());
		condition.addFilter("batchId",	job.getBatchId());  
		condition.addFilter("skuCd",	job.getSkuCd());
		List<Order> sources = this.queryManager.selectListWithLock(Order.class, condition);
		//box_pack_id
		List<BoxItem> itemList = new ArrayList<BoxItem>(sources.size());
		for(Order order : sources) { 
			BoxItem item = new BoxItem();
			item.setBoxPackId(boxPack.getId());
			item.setOrderId(order.getId());
			item.setOrderNo(order.getOrderNo());
			item.setOrderLineNo(order.getOrderLineNo());
			item.setOrderDetailId(order.getOrderDetailId());
			item.setComCd(order.getComCd());
			item.setShopCd(order.getShopCd());
			item.setSkuCd(order.getSkuCd());
			item.setSkuNm(order.getSkuNm());
			item.setPackType(order.getPackType());
			item.setPickedQty(order.getPickedQty());
			item.setCancelQty(order.getCancelQty());
			item.setCancelQty(order.getCancelQty());
			item.setDomainId(order.getDomainId());
			
			itemList.add(item);
		}
		 
		AnyOrmUtil.insertBatch(itemList, 100);
	}
	
	/**
	 * 주문 정보를 작업 정보 토대로 업데이트
	 *
	 * @param job
	 */
	private void updateOrderByConfirm(JobInstance job, int totalPickedQty) {
		// 1. 주문정보 조회
		List<String> statuses = ValueUtil.newStringList(LogisConstants.COMMON_STATUS_RUNNING, LogisConstants.COMMON_STATUS_WAIT);
		
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId());
		condition.addFilter("batchId",			job.getBatchId()); 
		condition.addFilter("skuCd",			job.getSkuCd());
		condition.addFilter("status",	"in",	statuses);

		condition.addOrder("orderQty",	true);
		condition.addOrder("pickedQty",	false);
		condition.addOrder("shopCd",	true);
		
		List<Order> sources = this.queryManager.selectListWithLock(Order.class, condition);   
 		
		for(Order source : sources) {
			if(totalPickedQty > 0) {
				int orderQty = source.getOrderQty();
				int pickedQty = source.getPickedQty();
				int remainQty = orderQty - pickedQty;
				
				// 1) 주문 라인 분류 종료
				if(totalPickedQty >= remainQty) {
					source.setPickedQty(source.getPickedQty() + remainQty);
					source.setStatus(LogisConstants.COMMON_STATUS_FINISHED);  
					totalPickedQty = totalPickedQty - remainQty;
					
				// 2) 주문 처리 수량 업데이트
				} else if(remainQty > totalPickedQty) {
					source.setPickedQty(source.getPickedQty() + totalPickedQty);
					totalPickedQty = 0; 
				} 
				
				source.setUpdatedAt(new Date());
				this.queryManager.update(source, "pickedQty","boxedQty","status","updatedAt");
			} else {
				break;
			}			
		}
	}
	
	/**
	 * 주문 정보를 작업 정보 토대로 업데이트
	 *
	 * @param job
	 */
	private void updateOrderByFullBox(JobInstance job,int totalPickedQty) {
		// 1. 주문정보 조회
		Query condition = AnyOrmUtil.newConditionForExecution(job.getDomainId());
		condition.addFilter("batchId",	job.getBatchId()); 
		condition.addFilter("skuCd",	job.getSkuCd());
		
		condition.addOrder("orderQty",	true);
		condition.addOrder("pickedQty",	false);
		condition.addOrder("shopCd",	true);
		
		List<Order> sources = this.queryManager.selectListWithLock(Order.class, condition);
 		
 		String boxId = job.getBoxId(); 
 		
		for(Order source : sources) { 
			if(totalPickedQty > 0) {
				int orderQty = source.getOrderQty();
				int boxedQty = source.getBoxedQty();
				int remainQty = orderQty - boxedQty;
				
				// 1) 주문 라인 분류 종료
				if(totalPickedQty >= remainQty) {
					source.setBoxId(boxId);
					source.setBoxedQty(source.getBoxedQty() + remainQty);
					source.setStatus(LogisConstants.COMMON_STATUS_FINISHED);
					totalPickedQty = totalPickedQty - remainQty;
					
				// 2) 주문 처리 수량 업데이트
				} else if(remainQty > totalPickedQty) {
					source.setBoxId(boxId);
					source.setBoxedQty(source.getBoxedQty() + totalPickedQty);
					source.setStatus(LogisConstants.COMMON_STATUS_RUNNING);
					totalPickedQty = 0; 
				}
				this.queryManager.update(source,"boxId","pickedQty","boxedQty","status","updatedAt");
			} else {
				break;
			}
		}; 
	}

}
