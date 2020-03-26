package operato.logis.das.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.service.impl.AbstractJobStatusService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Page;
import xyz.elidom.dbist.dml.Query;

/**
 * 반품 작업 상태 서비스
 * 
 * @author shortstop
 */
@Component("rtnJobStatusService")
public class RtnJobStatusService extends AbstractJobStatusService {

	@Override
	public List<JobInput> searchInputList(JobBatch batch, String equipCd, String stationCd, String selectedInputId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Page<JobInput> paginateInputList(JobBatch batch, String equipCd, String status, int page, int limit) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<JobInstance> searchInputJobList(JobBatch batch, JobInput input, String stationCd) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<JobInstance> searchInputJobList(JobBatch batch, Map<String, Object> condition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<JobInstance> searchPickingJobList(JobBatch batch, String stationCd, String classCd) {
		// TODO 표시기 점등을 위해 GW, IND 정보까지 모두 한꺼번에 조회 필요 - 참조 DpsJobStatusService.searchPickingJobList
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter("batchId", batch.getId());
		condition.addFilter("status", LogisConstants.JOB_STATUS_PICKING);
		condition.addFilter("classCd", classCd);
		condition.addFilter("pickingQty", ">=", 1);
		return this.queryManager.selectList(JobInstance.class, condition);
	}

	@Override
	public List<JobInstance> searchPickingJobList(JobBatch batch, Map<String, Object> condition) {
		// TODO Auto-generated method stub
		return null;
	}

}
