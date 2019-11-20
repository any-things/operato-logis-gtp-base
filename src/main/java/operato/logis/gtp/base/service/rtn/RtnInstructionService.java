package operato.logis.gtp.base.service.rtn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.gtp.base.query.store.GtpQueryStore;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.Order;
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
import xyz.elidom.sys.entity.User;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.BeanUtil;
import xyz.elidom.util.ValueUtil; 

@Component("rtnInstructionService")
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

			// 4. 작업 배치에 할당된 호기에 다른 배치가 진행 중인지 체크
//			Rack rack = Rack.findByRackCd(batch.getDomainId(), equipCd, false);
//			if(ValueUtil.isNotEmpty(rack.getBatchId())) {
//				// 호기에 다른 작업배치가 할당되어 있습니다
//				throw ThrowUtil.newValidationErrorWithNoLog(true, "MPS__ASSIGNED_ANOTHER_BATCH", ValueUtil.toList(batch.getEquipNm()));
//			}
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
					ValueUtil.newMap("domainId,batchId,userId,batchGroupId", batch.getDomainId(), batch.getId(), User.currentUser().getId(), batch.getBatchGroupId());
				
			String sql = QueryStore.getRtnPreprocessRackSummaryQuery();
			List<JobBatch> summaryBatchGroups = this.queryManager.selectListBySql(sql, params, JobBatch.class, 0, 0);
			//selectListWithLock
			
			// 2-2. 작업 차수 생성
			Integer jobBatchSeq = batch.getJobSeq();
			if(ValueUtil.isEmpty(jobBatchSeq) || jobBatchSeq == 0) {
				sql = QueryStore.getRtnCreateJobBatchSeqQuery();
				jobBatchSeq = this.queryManager.selectBySql(sql, params, Integer.class);
				jobBatchSeq += 1;
			}
			
			// 2-3. 호기별 작업 배치 데이터 생성
			for(int i = 0 ; i < summaryBatchGroups.size() ; i++) {
				JobBatch summaryBatch = summaryBatchGroups.get(i);
				batchGroups.add(this.createSubBatchByRack(batch, summaryBatch, jobBatchSeq, params, i == 0));
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
		
		// 1. OrderPreprocess 조회
		Query query = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		query.addFilter("batchId", batch.getId());  
		List<OrderPreprocess> source = this.queryManager.selectList(OrderPreprocess.class, query);
		
		// 2. Orders Update 처리
		this.updateOrderBy(batch,source);
		
		// 3. JOB INSTANCES 생성
		int result = this.generateJobInstancesBy(batch);
		
		// 4. WorkCell 생성
		this.generateWorkCellBy(batch);
		
		// 5. JobBatch 상태 업데이트
		batch.setStatus(JobBatch.STATUS_RUNNING);
		batch.setInstructedAt(new Date());
		this.queryManager.update(batch, "status");
		
		return result;
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
	private JobBatch createSubBatchByRack(JobBatch mainBatch, JobBatch summaryBatch, int jobSeq, Map<String, Object> params, boolean isFirst) {
		Long domainId = mainBatch.getDomainId();
		String newBatchId = isFirst ? mainBatch.getId() : LogisBaseUtil.newJobBatchId(domainId);
		String equipCd = summaryBatch.getEquipCd();
		JobBatch retBatch = null;

		if(isFirst) {
			// 첫번째 작업 배치 데이터는 기존 데이터에 호기 정보, 상태정보 update
			mainBatch.setEquipCd(summaryBatch.getEquipCd());
			mainBatch.setEquipNm(summaryBatch.getEquipNm());
			mainBatch.setJobSeq(jobSeq);
			this.queryManager.update(mainBatch, "equipCd", "equipNm", "jobSeq");
			retBatch =  mainBatch;

		} else {
			// 두번째 부터 기존 정보 복제 후 데이터 생성
			jobSeq = ValueUtil.isEmpty(equipCd) ? jobSeq + 1 : jobSeq;
			summaryBatch.setId(newBatchId);
			summaryBatch.setDomainId(domainId);
			summaryBatch.setStageCd(mainBatch.getStageCd());
			summaryBatch.setComCd(mainBatch.getComCd());
			summaryBatch.setJobType(mainBatch.getJobType());
			summaryBatch.setBatchGroupId(mainBatch.getBatchGroupId());
			summaryBatch.setJobDate(mainBatch.getJobDate());
			summaryBatch.setJobSeq(jobSeq); 
			summaryBatch.setBatchOrderQty(0);
			summaryBatch.setBatchPcs(0);
			summaryBatch.setWcsBatchNo(mainBatch.getWcsBatchNo());
			summaryBatch.setWmsBatchNo(mainBatch.getWmsBatchNo());
			summaryBatch.setParentOrderQty(mainBatch.getParentOrderQty()); 
			summaryBatch.setStatus(JobBatch.STATUS_WAIT);
			this.queryManager.insert(summaryBatch);
			retBatch =  summaryBatch;
		}

		String updateQuery = "UPDATE ORDER_PREPROCESSES SET BATCH_ID = :newBatchId, UPDATER_ID = :userId, UPDATED_AT = SYSDATE WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId ";
		if(ValueUtil.isEmpty(equipCd)) {
			updateQuery += "AND (EQUIP_CD IS NULL OR EQUIP_CD = '')";
		} else {
			updateQuery += "AND EQUIP_CD = :equipCd";
		}
		
		params.put("jobSeq", jobSeq);
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
	 *  JOB INSTANCES 생성
	 *
	 * @param batch
	 */
	private void updateOrderBy(JobBatch batch, List<OrderPreprocess> source) {

		List<Order> target = new ArrayList<Order>();
				
		for(OrderPreprocess item : source) {
			Order order = Order.find(batch.getDomainId(), batch.getId(), item.getCellAssgnCd(), true, true);
			order.setEquipCd(item.getEquipCd());
			order.setEquipNm(item.getEquipNm());
			order.setSubEquipCd(item.getSubEquipCd());
			order.setStatus("RUN");
			target.add(order);
		}
		
		AnyOrmUtil.updateBatch(target, 100, "equipCd", "equipNm","subEquipCd","status", "updatedAt");
	}
	
	
	/**
	 *  JOB INSTANCES 생성
	 *
	 * @param batch
	 */
	private int generateJobInstancesBy(JobBatch batch) {
		
		Map<String,Object> params =
				ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		
		// JOB INSTANCES 생성
		String insertQuery =this.QueryStore.getRtnGenerateJobInstancesQuery();
		int result = this.queryManager.executeBySql(insertQuery, params);
		return result;
	}
	
	/**
	 *  Work Cell 생성
	 *
	 * @param batch
	 */
	private int generateWorkCellBy(JobBatch batch) {
		
		Map<String,Object> params =
				ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		
		// Work Cell 생성
		String insertQuery =this.QueryStore.getRtnGenerateWorkCellQuery();
		int result = this.queryManager.executeBySql(insertQuery, params);
		return result;
	}
 
}
