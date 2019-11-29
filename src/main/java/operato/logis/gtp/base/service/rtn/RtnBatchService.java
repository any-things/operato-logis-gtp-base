package operato.logis.gtp.base.service.rtn;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.gtp.base.query.store.GtpQueryStore;
import operato.logis.gtp.base.service.model.RtnJobInstancesSummary;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.model.BatchProgressRate;
import xyz.anythings.base.service.api.IBatchService;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomValidationException;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.sys.util.ValueUtil;

@Component("rtnBatchService")
public class RtnBatchService extends AbstractQueryService implements IBatchService {

	/**
	 * 쿼리 스토어
	 */
	@Autowired
	private GtpQueryStore queryStore;
	
	@Override
	public String newJobBatchId(Long domainId, String stageCd, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BatchProgressRate dailyProgressRate(Long domainId, String stageCd, String jobDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JobBatch findRunningBatch(Long domainId, String stageCd, String equipType, String equipCd) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<JobBatch> searchRunningBatchList(Long domainId, String stageCd, String jobType, String jobDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<JobBatch> searchRunningMainBatchList(Long domainId, String stageCd, String jobType, String jobDate) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void isPossibleCloseBatch(JobBatch batch, boolean closeForcibly) { 
		// 1. 작업 배치 상태 체크
		if(ValueUtil.isNotEqual(batch.getStatus(), JobBatch.STATUS_RUNNING)) {
			// 진행 중인 작업배치가 아닙니다
			throw ThrowUtil.newStatusIsNotIng("terms.label.job_batch");
		}

		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter(new Filter("batchId", batch.getId()));

		// 2. batchId별 수신 주문이 존재하는지 체크
		int count = this.queryManager.selectSize(Order.class, condition);
		if(count == 0) {
			// 해당 배치의 주문정보가 없습니다 --> 주문을 찾을 수 없습니다.
			throw ThrowUtil.newNotFoundRecord("terms.label.order");
		}

		// 3. batchId별 작업 실행 데이터 체크
		count = this.queryManager.selectSize(JobInstance.class, condition);
		if(count == 0) {
			// 해당 배치의 작업실행 정보가 없습니다 --> 작업을 찾을 수 없습니다.
			throw ThrowUtil.newNotFoundRecord("terms.label.job");
		}

		// 4. batchId별 작업 실행 데이터 중에 완료되지 않은 것이 있는지 체크
		if(!closeForcibly) {
			condition.addFilter("status", OrmConstants.IN, LogisConstants.JOB_STATUS_WIPC);			

			if(this.queryManager.selectSize(JobInstance.class, condition) > 0) {
				// {0} 등 {1}개의 호기에서 작업이 끝나지 않았습니다.
				String msg = MessageUtil.getMessage("MPS_NOT_CLOSED_IN_REGIONS", "{0} 등 {1}개의 호기에서 작업이 끝나지 않았습니다.", ValueUtil.toList(batch.getEquipCd(), "1"));
				throw new ElidomValidationException(msg);
			}
		}	
	}

	@Override
	public int closeBatch(JobBatch batch, boolean forcibly) {
		// 1. 작업 마감 가능 여부 체크 
		this.isPossibleCloseBatch(batch, forcibly);

		// 2. 해당 배치에 대한 고정식이 아닌 호기들에 소속된 로케이션을 모두 찾아서 리셋
		this.resetRackAssignment(batch);

		// 3. OREDER_PREPROCESS 삭제
		this.deletePreprocess(batch);

		// 4. JobBatch 상태 변경
		this.updateJobBatchFinished(batch, new Date());
 
		return 1;
	}

	@Override
	public void isPossibleCloseBatchGroup(Long domainId, String batchGroupId, boolean closeForcibly) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int closeBatchGroup(Long domainId, String batchGroupId, boolean forcibly) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void isPossibleCancelBatch(JobBatch batch) {
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * TODO 각 모듈의 AssortService의 batchCloseAction으로 이동
	 * 해당 배치의 호기에 소속된 로케이션을 모두 찾아서 리셋
	 *
	 * @param batch
	 * @return
	 */
	protected int resetRackAssignment(JobBatch batch) {
		Map<String, Object> params = ValueUtil.newMap("domainId,equipCd,batchId,status", batch.getDomainId(), batch.getEquipCd(),batch.getId(),LogisConstants.COMMON_STATUS_WAIT);
	  	return this.queryManager.executeBySql("UPDATE RACKS SET STATUS = :status WHERE DOMAIN_ID = :domainId AND RACK_CD = :equipCd", params);
	}
	
	/**
	 * 주문 가공 정보를 모두 삭제한다.
	 *
	 * @param batch
	 * @return
	 */
	protected void deletePreprocess(JobBatch batch) {
		this.queryManager.executeBySql("DELETE ORDER_PREPROCESSES WHERE BATCH_ID= :batchId", ValueUtil.newMap("batchId", batch.getId()));
	}
	
	/**
	 * JobBatch 상태를 작업 완료 상태로, 배치 완료 시간을 endedAt으로 변경
	 *
	 * @param batch
	 * @param finishedAt
	 */
	protected void updateJobBatchFinished(JobBatch batch, Date finishedAt) {
		String query = queryStore.getRtnResetRackCellQuery();
		Map<String,Object> params = ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		
		RtnJobInstancesSummary result = this.queryManager.selectBySql(query, params, RtnJobInstancesSummary.class);
		
		batch.setResultPcs(result.getResultQty());
		batch.setUph(result.getJobUph()); 
		batch.setProgressRate(result.getProgressRate());
		batch.setStatus(JobBatch.STATUS_END);
		batch.setFinishedAt(finishedAt);
		
		this.queryManager.update(batch, "resultPcs","progressRate","uph", "status", "finishedAt");
	}

}
