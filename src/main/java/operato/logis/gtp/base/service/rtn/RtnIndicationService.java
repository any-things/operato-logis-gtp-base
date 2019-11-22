package operato.logis.gtp.base.service.rtn;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.service.api.IIndicationService;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.gw.service.api.IIndRequestService;
import xyz.anythings.sys.service.AbstractExecutionService;

/**
 * 반품 점, 소등 표시기 서비스
 * 
 * @author shortstop
 */
@Component("rtnIndicationService")
public class RtnIndicationService extends AbstractExecutionService implements IIndicationService {

	@Override
	public IIndRequestService getIndicatorRequestService(JobBatch batch) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IIndRequestService getIndicatorRequestService(String indType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Gateway> searchGateways(JobBatch batch) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<JobInstance> searchJobsForIndOn(JobBatch batch, Map<String, Object> condition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<JobInstance> indicatorsOn(JobBatch batch, boolean relight, List<JobInstance> jobList) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void indicatorOnForPick(JobInstance job, Integer firstQty, Integer secondQty, Integer thirdQty) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorOnForFullbox(JobInstance job) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorOnForPickEnd(JobInstance job, boolean finalEnd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorOff(Long domainId, String batchId, String jobType, String gwCd, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForBoxMapping(JobBatch batch, String gwCd, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForBoxMapping(JobBatch batch, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForNoBoxError(JobBatch batch, String gwCd, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForNoBoxError(JobBatch batch, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForString(Long domainId, String batchId, String jobType, String gwCd, String indCd,
			String firstSegStr, String secondSegStr, String thirdSegStr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForString(Long domainId, String batchId, String jobType, String indCd, String firstSegStr,
			String secondSegStr, String thirdSegStr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForCellCd(Long domainId, String batchId, String jobType, String gwCd, String indCd,
			String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForCellCd(Long domainId, String batchId, String jobType, String indCd, String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForIndCd(Long domainId, String batchId, String jobType, String gwCd, String indCd,
			String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForIndCd(Long domainId, String batchId, String jobType, String indCd, String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorsOnByInput(JobBatch batch, JobInput input, List<JobInstance> jobList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restoreIndicatorsOn(JobBatch batch, String equipZone) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restoreIndicatorsOn(JobBatch batch, Gateway gw) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restoreIndicatorsOn(JobBatch batch, int inputSeq, String equipZone, String mode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String nextIndicatorColor(JobInstance job, String prevColor) {
		// TODO Auto-generated method stub
		return null;
	}

}
