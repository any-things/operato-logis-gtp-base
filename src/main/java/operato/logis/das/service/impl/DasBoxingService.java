package operato.logis.das.service.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.sys.service.AbstractExecutionService;

/**
 * 출고용 박스 처리 서비스
 * 
 * @author shortstop
 */
@Component("dasBoxingService")
public class DasBoxingService extends AbstractExecutionService implements IBoxingService {

	@Override
	public String getJobType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JobConfigSet getJobConfigSet(String batchId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object assignBoxToCell(JobBatch batch, String cellCd, String boxId, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object resetBoxToCell(JobBatch batch, String cellCd, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack fullBoxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack partialFullboxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Integer fullboxQty,
			Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BoxPack> batchBoxing(JobBatch batch) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BoxPack cancelFullboxing(BoxPack box) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUsedBoxId(JobBatch batch, String boxId, boolean exceptionWhenBoxIdUsed) {
		// TODO Auto-generated method stub
		return false;
	}

}
