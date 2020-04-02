package operato.logis.das.service.impl;

import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.RtnQueryStore;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.model.BatchProgressRate;
import xyz.anythings.base.service.api.IBatchService;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomValidationException;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 반품 작업 배치 서비스
 * 
 * @author shortstop
 */
@Component("rtnBatchService")
public class RtnBatchService extends AbstractLogisService implements IBatchService {

	/**
	 * RTN 쿼리 스토어
	 */
	@Autowired
	private RtnQueryStore rtnQueryStore;
		
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
	public void closeBatch(JobBatch batch, boolean forcibly) {
		// 1. 작업 마감 가능 여부 체크 
		this.isPossibleCloseBatch(batch, forcibly);

		// 2. 해당 배치에 랙, 작업 셀 정보 리셋
		this.resetRacksAndCells(batch);

		// 3. OREDER_PREPROCESS 삭제
		this.deletePreprocess(batch);

		// 4. JobBatch 상태 변경
		this.updateJobBatchFinished(batch, new Date());
		
		// 5. 분류 서비스 배치 마감 API 호출 
		this.serviceDispatcher.getAssortService(batch).batchCloseAction(batch);
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
	 * 해당 배치의 랙, 작업 셀 정보 리셋
	 *
	 * @param batch
	 * @return
	 */
	protected void resetRacksAndCells(JobBatch batch) {
		Map<String, Object> params = ValueUtil.newMap("domainId,equipCd,batchId", batch.getDomainId(), batch.getEquipCd(), batch.getId());
	  	this.queryManager.executeBySql("UPDATE RACKS SET STATUS = null, BATCH_ID = null WHERE DOMAIN_ID = :domainId AND RACK_CD = :equipCd", params);
	  	this.queryManager.executeBySql("DELETE FROM WORK_CELLS WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId", params);
	}
	
	/**
	 * 주문 가공 정보를 모두 삭제한다.
	 *
	 * @param batch
	 * @return
	 */
	protected void deletePreprocess(JobBatch batch) {
		this.queryManager.executeBySql("DELETE ORDER_PREPROCESSES WHERE DOMAIN_ID = :domainId AND BATCH_ID= :batchId", ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId()));
	}
	
	/**
	 * 작업 배치를 마감 처리
	 * 
	 * @param batch
	 * @param finishedAt
	 */
	protected void updateJobBatchFinished(JobBatch batch, Date finishedAt) {
		// 배치 마감을 위한 물량 주문 대비 최종 실적 요약 정보 조회
		String query = this.rtnQueryStore.getRtnBatchResultSummaryQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		BatchProgressRate finalResult = this.queryManager.selectBySql(query, params, BatchProgressRate.class);
		
		// 작업 배치에 최종 결과 업데이트
		batch.setResultOrderQty(finalResult.getActualOrder());
		batch.setResultPcs(finalResult.getActualPcs());
		batch.setUph(finalResult.getUph()); 
		batch.setProgressRate(finalResult.getRatePcs());
		batch.setStatus(JobBatch.STATUS_END);
		batch.setFinishedAt(finishedAt);
		
		this.queryManager.update(batch, "resultOrderQty", "resultPcs", "progressRate", "uph", "status", "finishedAt");
	}

}
