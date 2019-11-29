package operato.logis.gtp.base.service.rtn;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.query.store.IndicatorQueryStore;
import xyz.anythings.base.service.api.IIndicationService;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.gw.entity.IndConfigSet;
import xyz.anythings.gw.service.IndicatorDispatcher;
import xyz.anythings.gw.service.api.IIndRequestService;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.elidom.sys.util.ValueUtil;

/**
 * 반품 점, 소등 표시기 서비스
 * 
 * @author shortstop
 */
@Component("rtnIndicationService")
public class RtnIndicationService extends AbstractExecutionService implements IIndicationService {
	
	/**
	 * 인디케이터 벤더별 서비스 디스패처 
	 */
	@Autowired
	private IndicatorDispatcher indicatorDispatcher;
	/**
	 * 표시기 관련 쿼리 스토어
	 */
	@Autowired
	private IndicatorQueryStore indQueryStore;

	@Override
	public IIndRequestService getIndicatorRequestService(JobBatch batch) {
		IIndRequestService indReqSvc = this.indicatorDispatcher.getIndicatorRequestServiceByBatch(batch.getId());
		
		if(indReqSvc == null) {
			IndConfigSet indConfigSet = batch.getIndConfigSet();
			this.indicatorDispatcher.addIndicatorConfigSet(batch.getId(), indConfigSet);
			indReqSvc = this.indicatorDispatcher.getIndicatorRequestServiceByBatch(batch.getId());
		}
		
		return indReqSvc;
	}

	@Override
	public IIndRequestService getIndicatorRequestService(String batchId) {
		IIndRequestService indReqSvc = this.indicatorDispatcher.getIndicatorRequestServiceByBatch(batchId);
		
		if(indReqSvc == null) {
			JobBatch batch = AnyEntityUtil.findEntityById(true, JobBatch.class, batchId);
			indReqSvc = this.getIndicatorRequestService(batch);
		}
		
		return indReqSvc;
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
		IIndRequestService indReqSvc = this.getIndicatorRequestService(job.getBatchId());
		
		if(ValueUtil.isEmpty(job.getGwPath())) {
			this.setIndInfoToJob(job);
		}
		
		indReqSvc.requestIndOnForPick(job.getDomainId(), job.getStageCd(), job.getJobType(), job.getGwPath(), job.getIndCd(), job.getId(), job.getColorCd(), firstQty, secondQty);
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
	public void indicatorOffAll(Long domainId, String batchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void indicatorListOff(Long domainId, String equipType, String equipCd, String stationCd) {
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
	
	/**
	 * 작업 정보에 표시기 점등을 위한 게이트웨이, 표시기 코드 정보를 찾아 설정
	 * 
	 * @param job
	 */
	@SuppressWarnings("rawtypes")
	public void setIndInfoToJob(JobInstance job) {
		String sql = this.indQueryStore.getSearchIndicatorsQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,stageCd,activeFlag,rackCd,indQueryFlag", job.getDomainId(), job.getStageCd(), true, job.getEquipCd(), true);
		List<Map> indList = this.queryManager.selectListBySql(sql, params, Map.class, 0, 0);
		Map indicator = ValueUtil.isNotEmpty(indList) ? indList.get(0) : null;
		
		if(indicator != null) {
			job.setIndCd(ValueUtil.toString(indicator.get("ind_cd")));
			job.setGwPath(ValueUtil.toString(indicator.get("gw_path")));
		}		
	}

}
