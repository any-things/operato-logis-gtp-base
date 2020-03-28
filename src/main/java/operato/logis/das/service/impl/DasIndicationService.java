package operato.logis.das.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.service.api.IDasIndicationService;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.query.store.IndicatorQueryStore;
import xyz.anythings.base.query.util.IndicatorQueryUtil;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.anythings.base.service.util.RuntimeIndServiceUtil;
import xyz.anythings.gw.GwConstants;
import xyz.anythings.gw.entity.Gateway;
import xyz.anythings.gw.entity.IndConfigSet;
import xyz.anythings.gw.service.IndicatorDispatcher;
import xyz.anythings.gw.service.api.IIndHandlerService;
import xyz.anythings.gw.service.api.IIndRequestService;
import xyz.anythings.gw.service.model.IIndOnInfo;
import xyz.anythings.gw.service.model.IndCommonReq;
import xyz.anythings.gw.service.util.BatchIndConfigUtil;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.elidom.sys.util.ValueUtil;

/**
 * 출고용 점, 소등 표시기 서비스
 * 
 * @author shortstop
 */
@Component("dasIndicationService")
public class DasIndicationService extends AbstractLogisService implements IDasIndicationService {

	/**
	 * 인디케이터 벤더별 서비스 디스패처 
	 */
	@Autowired
	protected IndicatorDispatcher indicatorDispatcher;
	/**
	 * 표시기 관련 쿼리 스토어
	 */
	@Autowired
	protected IndicatorQueryStore indQueryStore;

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
		return IndicatorQueryUtil.searchGatewayListByEquip(batch.getDomainId(), batch.getStageCd(), batch.getEquipType(), batch.getEquipCd(), null);
	}

	@Override
	public List<JobInstance> searchJobsForIndOn(JobBatch batch, Map<String, Object> condition) {		
		return this.serviceDispatcher.getJobStatusService(batch).searchPickingJobList(batch, condition);
	}
	
	@Override
	public void rebootGateway(JobBatch batch, Gateway gateway) {
		IIndHandlerService indHandler = this.indicatorDispatcher.getIndicatorHandlerServiceByBatch(batch.getId());
		indHandler.handleGatewayBootReq(gateway);
	}

	@Override
	public List<JobInstance> indicatorsOn(JobBatch batch, boolean relight, List<JobInstance> jobList) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch.getId());
		Map<String, List<IIndOnInfo>> indOnForPickList = RuntimeIndServiceUtil.buildIndOnList(!relight, batch, jobList, true);
		indReqSvc.requestIndListOn(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), GwConstants.IND_ACTION_TYPE_PICK, indOnForPickList);
		return jobList;
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
		IIndRequestService indReqSvc = this.getIndicatorRequestService(job.getBatchId());
		
		if(ValueUtil.isEmpty(job.getGwPath())) {
			this.setIndInfoToJob(job);
		}
		
		indReqSvc.requestFullbox(job.getDomainId(), job.getStageCd(), job.getJobType(), job.getGwPath(), job.getIndCd(), job.getId(), job.getColorCd());
	}

	@Override
	public void indicatorOnForPickEnd(JobInstance job, boolean finalEnd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(job.getBatchId());
		
		if(ValueUtil.isEmpty(job.getGwPath())) {
			this.setIndInfoToJob(job);
		}
		
		indReqSvc.requestIndEndDisplay(job.getDomainId(), job.getStageCd(), job.getJobType(), job.getGwPath(), job.getIndCd(), job.getId(), finalEnd);
	}

	@Override
	public void indicatorOffAll(JobBatch batch) {
		List<Gateway> gwList = this.searchGateways(batch);
		
		if(ValueUtil.isNotEmpty(gwList)) {
			IIndRequestService indReqSvc = this.getIndicatorRequestService(batch.getId());
			Long domainId = batch.getDomainId();
			String stageCd = batch.getStageCd();
			
			for(Gateway gw : gwList) {
				List<String> indCdList = IndicatorQueryUtil.searchIndCdList(gw, batch.getEquipType(), batch.getEquipCd());
				indReqSvc.requestIndListOff(domainId, stageCd, gw.getGwNm(), indCdList, false);
			}
		}
	}

	@Override
	public void indicatorListOff(Long domainId, String stageCd, String equipType, String equipCd, String stationCd) {
		List<Gateway> gwList = IndicatorQueryUtil.searchGatewayListByEquip(domainId, stageCd, equipType, equipCd, stationCd);
		
		if(ValueUtil.isNotEmpty(gwList)) {
			IIndRequestService indReqSvc = this.indicatorDispatcher.getIndicatorRequestServiceByStage(domainId, stageCd);
			
			for(Gateway gw : gwList) {
				List<String> indCdList = IndicatorQueryUtil.searchIndCdList(domainId, gw.getGwNm(), equipType, equipCd, stationCd);
				indReqSvc.requestIndListOff(domainId, stageCd, gw.getGwNm(), indCdList, false);
			}
		}
	}

	@Override
	public void indicatorOff(Long domainId, String stageCd, String gwPath, String indCd) {
		IIndRequestService indReqSvc = this.indicatorDispatcher.getIndicatorRequestServiceByStage(domainId, stageCd);
		indReqSvc.requestIndOff(domainId, stageCd, gwPath, indCd, false);
	}
	
	@Override
	public void indicatorOff(Long domainId, String stageCd, String indCd) {
		IIndRequestService indReqSvc = this.indicatorDispatcher.getIndicatorRequestServiceByStage(domainId, stageCd);
		String gwPath = IndicatorQueryUtil.findGatewayPathByIndCd(domainId, indCd);
		indReqSvc.requestIndOff(domainId, stageCd, gwPath, indCd, false);
	}
	
	@Override
	public void displayAllForBoxMapping(JobBatch batch) {
		String sql = "select i.ind_cd, g.gw_nm as gw_path from work_cells c inner join indicators i on c.domain_id = i.domain_id and c.ind_cd = i.ind_cd inner join gateways g on i.domain_id = g.domain_id and i.gw_cd = g.gw_cd where c.domain_id = :domainId and c.batch_id = :batchId and c.sku_cd is not null";
		Map<String, Object> params = ValueUtil.newMap("domainId,stageCd,activeFlag,rackCd,indQueryFlag", batch.getDomainId(), batch.getStageCd(), true, batch.getEquipCd(), true);
		List<IndCommonReq> indList = this.queryManager.selectListBySql(sql, params, IndCommonReq.class, 0, 0);
		
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch);
		for(IndCommonReq ind : indList) {
			indReqSvc.requestIndNoBoxDisplay(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), ind.getGwPath(), ind.getIndCd());
		}
	}

	@Override
	public void displayForBoxMapping(JobBatch batch, String gwPath, String indCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch);
		indReqSvc.requestIndNoBoxDisplay(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), gwPath, indCd);
	}

	@Override
	public void displayForBoxMapping(JobBatch batch, String indCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch);
		String gwPath = IndicatorQueryUtil.findGatewayPathByIndCd(batch.getDomainId(), indCd);
		indReqSvc.requestIndNoBoxDisplay(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), gwPath, indCd);
	}

	@Override
	public void displayForNoBoxError(JobBatch batch, String gwPath, String indCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch);
		indReqSvc.requestIndNoBoxDisplay(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), gwPath, indCd);
	}

	@Override
	public void displayForNoBoxError(JobBatch batch, String indCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch);
		String gwPath = IndicatorQueryUtil.findGatewayPathByIndCd(batch.getDomainId(), indCd);
		indReqSvc.requestIndNoBoxDisplay(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), gwPath, indCd);
	}

	@Override
	public void displayForString(Long domainId, String batchId, String stageCd, String jobType, String gwPath, String indCd, String showStr) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batchId);
		indReqSvc.requestShowString(domainId, stageCd, jobType, gwPath, indCd, indCd, showStr);
	}

	@Override
	public void displayForString(Long domainId, String batchId, String stageCd, String jobType, String indCd, String showStr) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batchId);
		String gwPath = IndicatorQueryUtil.findGatewayPathByIndCd(domainId, indCd);
		indReqSvc.requestShowString(domainId, stageCd, jobType, gwPath, indCd, indCd, showStr);
	}

	@Override
	public void displayForCellCd(Long domainId, String batchId, String stageCd, String jobType, String gwPath, String indCd, String cellCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batchId);
		indReqSvc.requestShowString(domainId, stageCd, jobType, gwPath, indCd, indCd, cellCd);
	}

	@Override
	public void displayForCellCd(Long domainId, String batchId, String stageCd, String jobType, String indCd, String cellCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batchId);
		String gwPath = IndicatorQueryUtil.findGatewayPathByIndCd(domainId, indCd);
		indReqSvc.requestShowString(domainId, stageCd, jobType, gwPath, indCd, indCd, cellCd);
	}

	@Override
	public void displayForIndCd(Long domainId, String batchId, String stageCd, String jobType, String gwPath, String indCd, String cellCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batchId);
		indReqSvc.requestShowString(domainId, stageCd, jobType, gwPath, indCd, indCd, indCd);
	}

	@Override
	public void displayForIndCd(Long domainId, String batchId, String stageCd, String jobType, String indCd, String cellCd) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batchId);
		String gwPath = IndicatorQueryUtil.findGatewayPathByIndCd(domainId, indCd);
		indReqSvc.requestShowString(domainId, stageCd, jobType, gwPath, indCd, indCd, indCd);
	}

	@Override
	public void indicatorsOnByInput(JobBatch batch, JobInput input, List<JobInstance> jobList) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch);
		Map<String, List<IIndOnInfo>> indOnForPickList = RuntimeIndServiceUtil.buildIndOnList(true, batch, jobList, true);
		indReqSvc.requestIndListOn(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), GwConstants.IND_ACTION_TYPE_PICK, indOnForPickList);		
	}

	@Override
	public void restoreIndicatorsOn(JobBatch batch, String equipZone) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch.getId());
		List<JobInstance> jobList = this.searchJobsForIndOn(batch, ValueUtil.newMap("status,stationCd", LogisConstants.JOB_STATUS_PICKING, equipZone));
		Map<String, List<IIndOnInfo>> indOnForPickList = RuntimeIndServiceUtil.buildIndOnList(false, batch, jobList, true);
		indReqSvc.requestIndListOn(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), GwConstants.IND_ACTION_TYPE_PICK, indOnForPickList);
	}

	@Override
	public void restoreIndicatorsOn(JobBatch batch) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch.getId());
		List<JobInstance> jobList = this.searchJobsForIndOn(batch, ValueUtil.newMap("status", LogisConstants.JOB_STATUS_PICKING));
		Map<String, List<IIndOnInfo>> indOnForPickList = RuntimeIndServiceUtil.buildIndOnList(false, batch, jobList, true);
		indReqSvc.requestIndListOn(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), GwConstants.IND_ACTION_TYPE_PICK, indOnForPickList);
	}

	@Override
	public void restoreIndicatorsOn(JobBatch batch, int inputSeq, String equipZone, String mode) {
		IIndRequestService indReqSvc = this.getIndicatorRequestService(batch.getId());
		List<JobInstance> jobList = this.searchJobsForIndOn(batch, ValueUtil.newMap("status,inputSeq,stationCd", LogisConstants.JOB_STATUS_PICKING, inputSeq, equipZone));
		Map<String, List<IIndOnInfo>> indOnForPickList = RuntimeIndServiceUtil.buildIndOnList(false, batch, jobList, true);
		indReqSvc.requestIndListOn(batch.getDomainId(), batch.getStageCd(), batch.getJobType(), GwConstants.IND_ACTION_TYPE_PICK, indOnForPickList);
	}

	@Override
	public String nextIndicatorColor(JobInstance job, String prevColor) {
		String[] rotations = BatchIndConfigUtil.getIndColorRotations(job.getBatchId());
		if(ValueUtil.isNotEmpty(rotations)) {
			int colorIdx = Arrays.asList(rotations).indexOf(prevColor);
			if(colorIdx >= rotations.length) {
				colorIdx = 0;
			} else {
				colorIdx = colorIdx + 1;
			}
			
			return rotations[colorIdx];
		} else {
			return LogisConstants.COLOR_RED;
		}
	}
	
	/**
	 * 작업 정보에 표시기 점등을 위한 게이트웨이, 표시기 코드 정보를 찾아 설정
	 * 
	 * @param job
	 */
	public void setIndInfoToJob(JobInstance job) {
		String sql = this.indQueryStore.getSearchIndicatorsQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,stageCd,activeFlag,rackCd,cellCd,indQueryFlag", job.getDomainId(), job.getStageCd(), true, job.getEquipCd(), job.getSubEquipCd(), true);
		List<IndCommonReq> indList = this.queryManager.selectListBySql(sql, params, IndCommonReq.class, 0, 0);
		IndCommonReq indicator = ValueUtil.isNotEmpty(indList) ? indList.get(0) : null;
		
		if(indicator != null) {
			job.setIndCd(indicator.getIndCd());
			job.setGwPath(indicator.getGwPath());
		}		
	}

}
