package operato.logis.das.service.impl;

import org.springframework.stereotype.Component;

import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.ICategorizeEvent;
import xyz.anythings.base.event.IClassifyErrorEvent;
import xyz.anythings.base.event.IClassifyInEvent;
import xyz.anythings.base.event.IClassifyOutEvent;
import xyz.anythings.base.event.IClassifyRunEvent;
import xyz.anythings.base.model.Category;
import xyz.anythings.base.service.api.IAssortService;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.impl.AbstractClassificationService;

/**
 * 출고용 분류 서비스 구현
 *
 * @author shortstop
 */
@Component("dasAssortService")
public class DasAssortService extends AbstractClassificationService implements IAssortService {

	@Override
	public String getJobType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Category categorize(ICategorizeEvent event) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object boxCellMapping(JobBatch batch, String cellCd, String boxId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String checkInput(JobBatch batch, String inputId, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object input(IClassifyInEvent inputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object classify(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object output(IClassifyOutEvent outputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkEndClassifyAll(JobBatch batch) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void batchStartAction(JobBatch batch) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void batchCloseAction(JobBatch batch) {
		// TODO Auto-generated method stub
		
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object inputSkuBundle(IClassifyInEvent inputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object inputSkuBox(IClassifyInEvent inputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object inputForInspection(IClassifyInEvent inputEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void confirmAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void cancelAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int splitAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int undoAssort(IClassifyRunEvent exeEvent) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public BoxPack fullBoxing(IClassifyOutEvent outEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack partialFullboxing(IClassifyOutEvent outEvent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack cancelBoxing(Long domainId, BoxPack boxPack) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JobInstance splitJob(JobInstance job, WorkCell workCell, int splitQty) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean checkStationJobsEnd(JobInstance job, String stationCd) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean checkCellAssortEnd(JobInstance job, boolean finalEndCheck) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean finishAssortCell(JobInstance job, WorkCell workCell, boolean finalEndFlag) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public JobInstance findLatestJobForBoxing(Long domainId, String batchId, String cellCd) {
		// TODO Auto-generated method stub
		return null;
	}

}
