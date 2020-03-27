package operato.logis.das.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import operato.logis.das.service.api.IDasIndicationService;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.gw.service.api.IIndRequestService;

/**
 * 출고용 점, 소등 표시기 서비스
 * 
 * @author shortstop
 */
@Component("dasIndicationService")
public class DasIndicationService extends AbstractLogisService implements IDasIndicationService {

	@Override
	public IIndRequestService getIndicatorRequestService(JobBatch batch) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IIndRequestService getIndicatorRequestService(String batchId) {
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
	public void rebootGateway(JobBatch batch, Gateway router) {
		// TODO Auto-generated method stub
		
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
	public void indicatorOffAll(JobBatch batch) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorListOff(Long domainId, String stageCd, String equipType, String equipCd, String stationCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorOff(Long domainId, String stageCd, String gwPath, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorOff(Long domainId, String stageCd, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForBoxMapping(JobBatch batch, String gwPath, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForBoxMapping(JobBatch batch, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForNoBoxError(JobBatch batch, String gwPath, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForNoBoxError(JobBatch batch, String indCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForString(Long domainId, String batchId, String stageCd, String jobType, String gwPath,
			String indCd, String showStr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForString(Long domainId, String batchId, String stageCd, String jobType, String indCd,
			String showStr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForCellCd(Long domainId, String batchId, String stageCd, String jobType, String gwPath,
			String indCd, String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForCellCd(Long domainId, String batchId, String stageCd, String jobType, String indCd,
			String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForIndCd(Long domainId, String batchId, String stageCd, String jobType, String gwPath,
			String indCd, String cellCd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayForIndCd(Long domainId, String batchId, String stageCd, String jobType, String indCd,
			String cellCd) {
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
	public void restoreIndicatorsOn(JobBatch batch) {
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

	@Override
	public void displayAllForBoxMapping(JobBatch batch) {
		// TODO Auto-generated method stub
		
	}

}
