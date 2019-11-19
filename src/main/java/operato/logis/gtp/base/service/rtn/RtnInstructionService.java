package operato.logis.gtp.base.service.rtn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import operato.logis.gtp.base.query.store.GtpQueryStore;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.entity.Rack;
import xyz.anythings.base.service.api.IInstructionService;
import xyz.anythings.base.util.LogisBaseUtil;
import xyz.anythings.sys.AnyConstants;
import xyz.anythings.sys.service.AbstractQueryService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.SysConstants;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil; 

public class RtnInstructionService  extends AbstractQueryService  implements IInstructionService{

	@Autowired
	private GtpQueryStore QueryStore;
	
	@Override
	public Map<String, Object> searchInstructionData(JobBatch batch, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * 무오더 반품 유형이면 작업 지시 완료 
	 *
	 * @param batch
	 * @param rackList
	 * @param params
	 * @return
	 */
	@Override
	public int instructBatch(JobBatch batch, List<String> equipIdList, Object... params) {
		int instructCount = 0;

		if(this.beforeInstructBatch(batch, equipIdList)) {
			instructCount += this.doInstructBatch(batch, equipIdList);
			this.afterInstructBatch(batch, equipIdList);
		}

		return instructCount;
	}

	@Override
	public int instructTotalpicking(JobBatch batch, List<String> equipIdList, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int mergeBatch(JobBatch mainBatch, JobBatch newBatch, Object... params) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int cancelInstructionBatch(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
	/**
	 * 작업 지시 전 처리 액션
	 *
	 * @param batch
	 * @param rackList
	 * @return
	 */
	protected boolean beforeInstructBatch(JobBatch batch, List<String> equipIdList) {
		// 1. 배치 상태가 작업 지시 상태인지 체크
		if(ValueUtil.isNotEqual(batch.getStatus(), JobBatch.STATUS_READY)) {
			// 주문 가공 대기' 상태가 아닙니다
			throw ThrowUtil.newValidationErrorWithNoLog(MessageUtil.getTerm("terms.text.is_not_wait_state", "JobBatch status is not WAIT"));
		}

		// 2. 무오더 반품이 아닌 경우
		if(!batch.isRtn3Batch()) {
			String equipCd = batch.getEquipCd();

			// 3. 작업 배치에 할당되었는지 체크
			if(ValueUtil.isEmpty(equipCd)) {
				// 작업배치에 호기가 할당되지 않았습니다.
				throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS_A_NOT_ASSIGNED_TO", "terms.label.job_batch", "terms.menu.");
			}

			// 4. 작업 배치에 할당된 호기에 다른 배치가 진행 중인지 체크
			Rack rack = Rack.findByRackCd(batch.getDomainId(), equipCd, true);
			if(ValueUtil.isNotEmpty(rack.getBatchId())) {
				// 호기에 다른 작업배치가 할당되어 있습니다
				throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS__ASSIGNED_ANOTHER_BATCH", ValueUtil.toList(batch.getEquipNm()));
			}
		}

		return true;
	}
	

	
	
	 
	
	/**
	 * 작업 지시 처리 로직
	 *
	 * @param batch
	 * @param regionList
	 * @return
	 */
	protected int doInstructBatch(JobBatch batch, List<String> regionList) {
		// 주문 가공 완료 처리 주문 가공 배치 그룹
		List<JobBatch> batchGroups = new ArrayList<JobBatch>();
		
		// 1. 무오더 반품 배치 작업 지시 처리
		if(batch.isRtn3Batch()) {
			return this.doInstructNoOrderBatch(batch);

		// 2. 일반 반품 배치 작업 지시 처리
		} else {
			// 2-1. 주문 가공 정보 호기 요약 정보 확인
			Map<String,Object> params =
					ValueUtil.newMap("domainId,batchId,batchGroupId", batch.getDomainId(), batch.getId(),batch.getBatchGroupId());
			String sql = QueryStore.getRtnPreprocessRackSummaryQuery();
			List<JobBatch> summaryBatchGroups = this.queryManager.selectListBySql(sql, params, JobBatch.class, 0, 0);
		
			// 2-2. 작업 차수 생성
			Integer jobBatchSeq = batch.getJobSeq();
			if(ValueUtil.isEmpty(jobBatchSeq) || jobBatchSeq == 0) {
				jobBatchSeq = this.queryManager.selectBySql("select nvl(max(job_batch_seq), 0) from tb_job_batch where domain_id = :domainId and batch_group_id = :batchGroupId ", params, Integer.class);
				jobBatchSeq += 1;
			}
			
			// 2-3. 호기별 작업 배치 데이터 생성
			for(int i = 0 ; i < summaryBatchGroups.size() ; i++) {
				JobBatch summaryBatch = summaryBatchGroups.get(i);
				batchGroups.add(this.createSubBatchBy(batch, summaryBatch, jobBatchSeq, params, i == 0));
			}
			
			return this.doInstructGeneralBatch(batch);
		}
	}
	
	/**
	 * 일반 호기 작업 배치에 대한 작업 지시(Process 처리)
	 *
	 * @param batch
	 * @return
	 */
	protected int doInstructGeneralBatch(JobBatch batch) {
		
		// 1. 조건 생성
		Long domainId = batch.getDomainId();
		Map<String, Object> params = ValueUtil.newMap("P_IN_DOMAIN_ID,P_IN_BATCH_ID", domainId, batch.getId());
		// 2. JobBatch 조회
		Map<?, ?> result = this.queryManager.callReturnProcedure("SP_RTN_INSTRUCT_JOB", params, Map.class);
		// 3. 결과 파싱
		int resCode = ((java.math.BigDecimal)result.get("P_OUT_RESULT_CODE")).toBigInteger().intValueExact();
		String resMessage = ValueUtil.isNotEmpty(result.get("P_OUT_MESSAGE")) ? result.get("P_OUT_MESSAGE").toString() : SysConstants.EMPTY_STRING;
		int createdCount = ((java.math.BigDecimal)result.get("P_OUT_CREATED_COUNT")).toBigInteger().intValueExact();

		if(resCode == 0) {
			if(ValueUtil.isNotEmpty(resMessage)) {
				throw new ElidomRuntimeException(resMessage);
			}
		} else {
			throw new ElidomRuntimeException(resMessage);
		}

		// 4. 배치 시작 액션 처리
	//	this.getAssortService(batch).batchStartAction(batch);

		// 5. 생성 개수
		return createdCount;
	}
	
	/**
	 * 무오더 반품 배치 작업 지시
	 *
	 * @param batch
	 * @return
	 */
	protected int doInstructNoOrderBatch(JobBatch batch) {
		// 1. 호기 조회
		Query query = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		query.addFilter("batchId", batch.getId());
		OrderPreprocess process = this.queryManager.select(OrderPreprocess.class, query);
		
		String equipCd = process.getEquipCd();
		
		// 2. 작업 지시 처리
		batch.setEquipCd(equipCd);
		batch.setStatus(JobBatch.STATUS_RUNNING);
		batch.setInstructedAt(new Date());
		this.queryManager.update(batch, AnyConstants.ENTITY_FIELD_STATUS, "equipCd", "instructedAt");

		// 3. 배치 시작 액션 처리
//		this.getAssortService(batch).batchStartAction(batch);

		// 4. 생성 개수
		return 1;
	}
	
	/**
	 * 작업 배치를 여러 호기로 설정한 후에 설정한 가공의 호기별로 실제 작업 배치 생성(JobBatch)
	 *
	 * @param mainBatch
	 * @param summaryBatch
	 * @param params
	 * @param isFirst
	 */
	private JobBatch createSubBatchBy(JobBatch mainBatch, JobBatch summaryBatch, int jobBatchSeq, Map<String, Object> params, boolean isFirst) {
		Long domainId = mainBatch.getDomainId();
		String newBatchId = isFirst ? mainBatch.getId() : LogisBaseUtil.newJobBatchId(domainId);
		String equipCd = summaryBatch.getEquipCd();
		JobBatch retBatch = null;

		if(isFirst) {
			// 첫번째 작업 배치 데이터는 기존 데이터에 호기 정보, 상태정보 update
			mainBatch.setEquipCd(summaryBatch.getEquipCd());
			mainBatch.setEquipNm(summaryBatch.getEquipNm());
			mainBatch.setJobSeq(jobBatchSeq);
			this.queryManager.update(mainBatch, "equipCd", "equipNm", "jobBatchSeq");
			retBatch =  mainBatch;

		} else {
			// 두번째 부터 기존 정보 복제 후 데이터 생성
			jobBatchSeq = ValueUtil.isEmpty(equipCd) ? jobBatchSeq + 1 : jobBatchSeq;
			params.put("jobBatchSeq", jobBatchSeq);
			summaryBatch.setId(newBatchId);
			summaryBatch.setDomainId(domainId);
			summaryBatch.setStageCd(mainBatch.getStageCd());
			summaryBatch.setComCd(mainBatch.getComCd());
			summaryBatch.setJobType(mainBatch.getJobType());
			summaryBatch.setBatchGroupId(mainBatch.getBatchGroupId());
			summaryBatch.setJobDate(mainBatch.getJobDate());
			summaryBatch.setJobSeq(jobBatchSeq); 
			summaryBatch.setBatchOrderQty(0);
			summaryBatch.setBatchPcs(0);
			summaryBatch.setWcsBatchNo(mainBatch.getWcsBatchNo());
			summaryBatch.setWmsBatchNo(mainBatch.getWmsBatchNo());
			summaryBatch.setParentOrderQty(mainBatch.getParentOrderQty()); 
			summaryBatch.setStatus(JobBatch.STATUS_WAIT);
			this.queryManager.insert(summaryBatch);
			retBatch =  summaryBatch;
		}

		String updateQuery = "UPDATE TB_RTN_PREPROCESS SET BATCH_ID = :newBatchId, UPDATER_ID = :userId, UPDATED_AT = SYSDATE WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId ";
		if(ValueUtil.isEmpty(equipCd)) {
			updateQuery += "AND (EQUIP_CD IS NULL OR EQUIP_CD = '')";
		} else {
			updateQuery += "AND EQUIP_CD = :equipCd";
		}

		params.put("newBatchId", newBatchId);
		params.put("equipCd", equipCd);
		this.queryManager.executeBySql(updateQuery, params);
		
		if(!ValueUtil.isEmpty(equipCd)) 
			params.put("rackEmpty", equipCd);
			
		// 해당 배치의 주문 정보들의 호기, 작업 배치 ID 업데이트
		updateQuery =this.QueryStore.getRtnBatchIdOfOrderUpdateQuery();
		this.queryManager.executeBySql(updateQuery, params);
		return retBatch;
	}
	
	
	
	
	/**
	 * 작업 지시 후 처리 액션
	 *
	 * @param batch
	 * @param regionList
	 * @return
	 */
	protected void afterInstructBatch(JobBatch batch, List<String> equipIdList) {
		// 주문 가공 이벤트 전송
//		InstructionEvent event = new InstructionEvent(batch.getDomainId(), batch, null);
//		BeanUtil.get(EventPublisher.class).publishEvent(event);
	}
}
