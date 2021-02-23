package operato.logis.das.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.DasQueryStore;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Order;
import xyz.anythings.base.event.main.BatchCloseEvent;
import xyz.anythings.base.service.api.IBatchService;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.service.ICustomService;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Filter;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.orm.OrmConstants;
import xyz.elidom.sys.entity.User;
import xyz.elidom.sys.util.MessageUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 출고 작업 배치 서비스
 * 
 * @author shortstop
 */
@Component("dasBatchService")
public class DasBatchService extends AbstractLogisService implements IBatchService {

	/**
	 * 커스텀 서비스 - 작업 완료 전 처리
	 */
	private static final String DIY_PRE_BATCH_STOP = "diy-das-pre-batch-stop";
	/**
	 * 커스텀 서비스 - 작업 완료 후 처리
	 */
	private static final String DIY_POST_BATCH_STOP = "diy-das-post-batch-stop";
	/**
	 * 커스텀 서비스
	 */
	@Autowired
	protected ICustomService customService;
	/**
	 * DAS 쿼리 스토어
	 */
	@Autowired
	private DasQueryStore dasQueryStore;
		
	@Override
	public void isPossibleCloseBatch(JobBatch batch, boolean closeForcibly) {
		// 1. 배치 마감 전 처리 이벤트 전송
		BatchCloseEvent event = new BatchCloseEvent(batch, SysEvent.EVENT_STEP_BEFORE);
		event = (BatchCloseEvent)this.eventPublisher.publishEvent(event);
		
		// 2. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			return;
		}
		
		// 3. 작업 배치 상태 체크
		if(ValueUtil.isNotEqual(batch.getStatus(), JobBatch.STATUS_RUNNING)) {
			// 진행 중인 작업배치가 아닙니다
			throw ThrowUtil.newStatusIsNotIng("terms.label.job_batch");
		}

		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		condition.addFilter(new Filter("batchId", batch.getId()));

		// 4. batchId별 수신 주문이 존재하는지 체크
		int count = this.queryManager.selectSize(Order.class, condition);
		if(count == 0) {
			// 해당 배치의 주문정보가 없습니다 --> 주문을 찾을 수 없습니다.
			throw ThrowUtil.newNotFoundRecord("terms.label.order");
		}

		// 5. batchId별 작업 실행 데이터 체크
		count = this.queryManager.selectSize(JobInstance.class, condition);
		if(count == 0) {
			// 해당 배치의 작업실행 정보가 없습니다 --> 작업을 찾을 수 없습니다.
			throw ThrowUtil.newNotFoundRecord("terms.label.job");
		}

		// 6. batchId별 작업 실행 데이터 중에 완료되지 않은 것이 있는지 체크
		if(!closeForcibly) {
			condition.addFilter("status", OrmConstants.IN, LogisConstants.JOB_STATUS_WIPC);
			if(this.queryManager.selectSize(JobInstance.class, condition) > 0) {
				// {0} 등 {1}개의 호기에서 작업이 끝나지 않았습니다.
				String msg = MessageUtil.getMessage("ASSORTING_NOT_FINISHED_IN_RACKS", "{0} 등 {1}개의 랙에서 작업이 끝나지 않았습니다.", ValueUtil.toList(batch.getEquipCd(), "1"));
				throw ThrowUtil.newValidationErrorWithNoLog(msg);
			}
		}
		
		// 7. 커스텀 서비스 호출
		this.customService.doCustomService(batch.getDomainId(), DIY_PRE_BATCH_STOP, ValueUtil.newMap("batch", batch));
	}

	@Override
	public void closeBatch(JobBatch batch, boolean forcibly) {
		// 1. 작업 마감 가능 여부 체크 
		this.isPossibleCloseBatch(batch, forcibly);

		// 2. 배치 마감 후 처리 이벤트 전송
		BatchCloseEvent event = new BatchCloseEvent(batch, SysEvent.EVENT_STEP_AFTER);
		event = (BatchCloseEvent)this.eventPublisher.publishEvent(event);
		
		// 3. 이벤트 취소라면 ...
		if(event.isAfterEventCancel()) {
			return;
		}
		
		// 4. 해당 배치에 랙, 작업 셀 정보 리셋
		this.resetRacksAndCells(batch);

		// 5. 주문 가공 정보 삭제
		this.deletePreprocess(batch);

		// 6. JobBatch 상태 변경
		this.updateJobBatchFinished(batch, new Date());
		
		// 7. 분류 서비스 배치 마감 API 호출
		this.serviceDispatcher.getAssortService(batch).batchCloseAction(batch);
		
		// 8. 커스텀 서비스 호출
		this.customService.doCustomService(batch.getDomainId(), DIY_POST_BATCH_STOP, ValueUtil.newMap("batch", batch));
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
		this.queryManager.executeBySql("DELETE FROM ORDER_PREPROCESSES WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId", ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId()));
	}
	
	/**
	 * 작업 배치를 마감 처리
	 * 
	 * @param batch
	 * @param finishedAt
	 */
	protected void updateJobBatchFinished(JobBatch batch, Date finishedAt) {
		// 배치 마감을 위한 물량 주문 대비 최종 실적 요약 정보 조회
		String query = this.dasQueryStore.getDasBatchResultSummaryQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		JobBatch finalResult = this.queryManager.selectBySql(query, params, JobBatch.class);
		
		// 작업 배치에 최종 결과 업데이트
		batch.setUph(finalResult.getUph());
		batch.setEquipRuntime(finalResult.getEquipRuntime());
		batch.setStatus(JobBatch.STATUS_END);
		batch.setFinishedAt(finishedAt);
		this.queryManager.update(batch, "uph", "equipRuntime", "status", "finishedAt");
	}

	@Override
	public void isPossibleChangeEquipment(JobBatch batch, String toEquipCd) {
		// 1. 랙이 다른 랙인지 체크
		String fromRackCd = batch.getEquipCd();
		if(ValueUtil.isNotEmpty(fromRackCd) && ValueUtil.isEqualIgnoreCase(batch.getEquipCd(), toEquipCd)) {
			throw ThrowUtil.newValidationErrorWithNoLog("현재 배치의 랙과 다른 랙을 선택하세요.");
		}
		
		// 2. 배치 상태 체크
		String status = batch.getStatus();
		if(ValueUtil.isNotEqual(status, JobBatch.STATUS_WAIT) && ValueUtil.isNotEqual(status, JobBatch.STATUS_READY)) {
			throw ThrowUtil.newValidationErrorWithNoLog("작업 진행 전에만 랙 전환이 가능합니다.");
		}
		
		// 3. 동일 일자, 동일 차수, 동일 호기에 할당된 작업 배치가 있는지 체크
		String sql = "select id from job_batches where domain_id = :domainId and job_date = :jobDate and job_seq = :jobSeq and equip_type = :equipType and equip_cd = :equipCd and status in (:statuses)";
		Map<String, Object> params = ValueUtil.newMap("domainId,jobDate,jobSeq,equipType,equipCd,statuses", batch.getDomainId(), batch.getJobDate(), batch.getJobSeq(), batch.getEquipType(), toEquipCd, ValueUtil.toList(JobBatch.STATUS_WAIT, JobBatch.STATUS_READY));
		if(this.queryManager.selectSizeBySql(sql, params) > 0) {
			throw ThrowUtil.newValidationErrorWithNoLog("동일 일자, 차수, 호기에 할당된 작업배치가 이미 존재합니다.");
		}
		
		// 4. From Cell Count
		sql = "select count(*) from cells where domain_id = :domainId and equip_type = :equipType and equip_cd = :equipCd and active_flag = :activeFlag";
		Map<String, Object> condition = ValueUtil.newMap("domainId,equipType,equipCd,activeFlag", batch.getDomainId(), batch.getEquipType(), batch.getEquipCd(), true);
		int fromCellCount = this.queryManager.selectBySql(sql, condition, Integer.class);
		
		// 5. To Cell Count
		sql = "select count(*) from cells where domain_id = :domainId and equip_type = :equipType and equip_cd = :equipCd and active_flag = :activeFlag";
		condition.put("equipCd", toEquipCd);
		int toCellCount = this.queryManager.selectBySql(sql, condition, Integer.class);
		
		// 6. 셀 개수 체크
		if(fromCellCount > toCellCount) {
			throw ThrowUtil.newValidationErrorWithNoLog("전환할 랙의 셀 개수와 이전 랙의 셀 개수가 다릅니다.");
		}
	}

	@Override
	public void changeEquipment(JobBatch batch, String toEquipCd) {
		Long domainId = batch.getDomainId();
		Query condition = AnyOrmUtil.newConditionForExecution(domainId);
		condition.addSelect("cell_cd");
		condition.addFilter("equipCd", batch.getEquipCd());
		condition.addFilter("activeFlag", true);
		condition.addOrder("cellSeq", true);
		
		// 1. 랙 이름 조회
		String sql = "SELECT RACK_NM FROM RACKS WHERE DOMAIN_ID = :domainId AND RACK_CD = :rackCd";
		String toEquipNm = this.queryManager.selectBySql(sql, ValueUtil.newMap("domainId,rackCd", domainId, toEquipCd), String.class);
		
		// 2. From 랙을 셀 순서대로 조회
		List<Cell> fromCellList = this.queryManager.selectList(Cell.class, condition);
		
		// 3. To 랙을 셀 순서대로 조회
		condition.removeFilter("equipCd");
		condition.addFilter("equipCd", toEquipCd);
		List<Cell> toCellList = this.queryManager.selectList(Cell.class, condition);
		
		// 4. 주문 가공 / 주문의 From Cell과 To Cell을 그대로 이동, TODO 아래 코드 체크 필요
		String newBatchId = batch.getId().replace(LogisConstants.DASH + batch.getEquipCd(), LogisConstants.DASH + toEquipCd);
		String preprocessSql = "UPDATE ORDER_PREPROCESSES SET BATCH_ID = :newBatchId, EQUIP_CD = :toEquipCd, EQUIP_NM = :toEquipNm, CLASS_CD = :toEquipCd, SUB_EQUIP_CD = :toCellCd WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId AND SUB_EQUIP_CD = :fromCellCd";
		String orderSql = "UPDATE ORDERS SET BATCH_ID = :newBatchId, EQUIP_CD = :toEquipCd, EQUIP_NM = :toEquipNm, SUB_EQUIP_CD = :toCellCd WHERE DOMAIN_ID = :domainId AND BATCH_ID = :batchId AND SUB_EQUIP_CD = :fromCellCd";
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,newBatchId,toEquipCd,toEquipNm", domainId, batch.getId(), newBatchId, toEquipCd, toEquipNm);
		
		for(int i = 0 ; i < fromCellList.size() ; i++) {
			if(toCellList.size() > i) {
				Cell fromCell = fromCellList.get(i);
				Cell toCell = toCellList.get(i);
				params.put("fromCellCd", fromCell.getCellCd());
				params.put("toCellCd", toCell.getCellCd());
			
				this.queryManager.executeBySql(preprocessSql, params);
				this.queryManager.executeBySql(orderSql, params);
			}
		}
		
		// 5. 작업 배치 변경
		params.put("updaterId", User.currentUser() != null ? User.currentUser().getId() : null);
		params.put("updatedAt", new Date());
		sql = "UPDATE JOB_BATCHES SET EQUIP_CD = :toEquipCd, EQUIP_NM = :toEquipNm, ID = :newBatchId, UPDATER_ID = :updaterId, UPDATED_AT = :updatedAt WHERE DOMAIN_ID = :domainId AND ID = :batchId";
		this.queryManager.executeBySql(sql, params);
	}

}
