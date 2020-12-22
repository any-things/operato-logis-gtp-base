package operato.logis.das.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.DasConfigConstants;
import operato.logis.das.service.util.DasBatchJobConfigUtil;
import xyz.anythings.base.LogisCodeConstants;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.BoxPack;
import xyz.anythings.base.entity.Cell;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobConfigSet;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.WorkCell;
import xyz.anythings.base.event.box.UndoBoxingEvent;
import xyz.anythings.base.event.classfy.ClassifyOutEvent;
import xyz.anythings.base.service.api.IBoxingService;
import xyz.anythings.base.service.impl.AbstractLogisService;
import xyz.anythings.base.service.util.BatchJobConfigUtil;
import xyz.anythings.gw.entity.Indicator;
import xyz.anythings.sys.event.model.SysEvent;
import xyz.anythings.sys.service.ICustomService;
import xyz.anythings.sys.util.AnyEntityUtil;
import xyz.anythings.sys.util.AnyOrmUtil;
import xyz.elidom.dbist.dml.Query;
import xyz.elidom.exception.server.ElidomRuntimeException;
import xyz.elidom.sys.util.DateUtil;
import xyz.elidom.sys.util.ThrowUtil;
import xyz.elidom.util.ValueUtil;

/**
 * 출고용 박스 처리 서비스
 * 
 * @author shortstop
 */
@Component("dasBoxingService")
public class DasBoxingService extends AbstractLogisService implements IBoxingService {

	/**
	 * 커스텀 서비스 - 풀 박스 전 처리
	 */
	private static final String DIY_DAS_BEFORE_FULLBOX = "diy-das-before-fullbox";
	/**
	 * 커스텀 서비스 - 풀 박스 후 처리
	 */
	private static final String DIY_DAS_AFTER_FULLBOX = "diy-das-after-fullbox";
	
	/**
	 * 커스텀 서비스
	 */
	@Autowired
	protected ICustomService customService;

	@Override
	public String getJobType() {
		return LogisConstants.JOB_TYPE_DAS;
	}

	@Override
	public JobConfigSet getJobConfigSet(String batchId) {
		return BatchJobConfigUtil.getConfigSetService().getConfigSet(batchId);
	}
	
	@Override
	public boolean isUsedBoxId(JobBatch batch, String boxId, boolean exceptionWhenBoxIdUsed) {
		Query condition = AnyOrmUtil.newConditionForExecution(batch.getDomainId());
		String boxIdUniqueScope = BatchJobConfigUtil.getBoxIdUniqueScope(batch, LogisConstants.BOX_ID_UNIQUE_SCOPE_GLOBAL);
		
		switch(boxIdUniqueScope) {
			case LogisConstants.BOX_ID_UNIQUE_SCOPE_GLOBAL :
				condition.addFilter("boxId", boxId);
				break;
				
			case LogisConstants.BOX_ID_UNIQUE_SCOPE_DAY :
				condition.addFilter("jobDate", batch.getJobDate());
				condition.addFilter("boxId", boxId);
				break;
				
			case LogisConstants.BOX_ID_UNIQUE_SCOPE_BATCH :
				condition.addFilter("batchId", batch.getId());
				condition.addFilter("boxId", boxId);
				break;
		}
		
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
		if(boxPack != null && exceptionWhenBoxIdUsed) {
			throw new ElidomRuntimeException("박스 ID [" + boxId + "]는 이미 사용한 박스입니다.");
		}
		
		return boxPack != null;
	}

	@Override
	public Object assignBoxToCell(JobBatch batch, String cellCd, String boxId, Object... params) {
		Long domainId = batch.getDomainId();
		
		// 1. Box 사용 여부 체크
		this.isUsedBoxId(batch, boxId, true);
		
		// 2. 이미 매핑되었는지 여부 체크 
		String sql = "select cell_cd from work_cells where domain_id = :domainId and batch_id = :batchId and box_id = :boxId";
		Map<String, Object> checkParams = ValueUtil.newMap("domainId,batchId,boxId", domainId, batch.getId(), boxId);
		int count = this.queryManager.selectSizeBySql(sql, checkParams);
		if(count > 0) {
			throw ThrowUtil.newValidationErrorWithNoLog("박스 ID가 다른 셀에 이미 매핑되어 있습니다.");
		}
		
		// 3. 셀 체크
		Cell cell = AnyEntityUtil.findEntityBy(domainId, false, Cell.class, null, "domainId,equipCd,cellCd", batch.getDomainId(), batch.getEquipCd(), cellCd);
		if(cell == null) {
			throw ThrowUtil.newValidationErrorWithNoLog("랙 [" + batch.getEquipCd() + "]에 셀 [" + cellCd + "]이 존재하지 않습니다.");
		}
		
		// 4. 워크 셀 체크
		WorkCell workCell = AnyEntityUtil.findEntityBy(domainId, false, WorkCell.class, null, "batchId,cellCd", batch.getId(), cellCd);
		if(workCell == null) {
			throw ThrowUtil.newValidationErrorWithNoLog("작업배치 [" + batch.getId() + "]에 매핑된 셀이 존재하지 않습니다.");
		}
		
		// 5. 셀에 박스 ID가 매핑되어 있는지 체크
		if(ValueUtil.isNotEmpty(workCell.getBoxId())) {
			throw ThrowUtil.newValidationErrorWithNoLog("셀 [" + cellCd + "]에 이미 매핑된 박스가 존재합니다.");
		}
		
		// 6. 워크 셀에 박스 ID 설정
		workCell.setBoxId(boxId);
		
		// 7. 박스 ID 매핑
		this.queryManager.update(workCell, "boxId");
		
		// 8. 클라이언트에 할당 정보 리턴
		return workCell;
	}

	@Override
	public Object resetBoxToCell(JobBatch batch, String cellCd, Object... params) {
		// 작업 WorkCell 조회 후 BoxId를 클리어
		WorkCell cell = AnyEntityUtil.findEntityBy(batch.getDomainId(), true, WorkCell.class, null, "batchId,cellCd", batch.getId(), cellCd);
		cell.setBoxId(null);
		this.queryManager.update(cell, "boxId", "updatedAt");
		return cell;
	}

	@Override
	public BoxPack fullBoxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Object... params) {
		// 1. 작업 리스트 존재 여부 체크
		if(ValueUtil.isEmpty(jobList)) {
			throw ThrowUtil.newValidationErrorWithNoLog(true, "NO_JOBS_FOR_BOXING");
		}
		
		// 2. 박스 선 매핑시 셀 - 박스 매핑이 안 되어 있는 경우 표시기 에러 표시
		String boxMappingPoint = DasBatchJobConfigUtil.getCellBoxMappingPoint(batch);
		if(ValueUtil.isEqualIgnoreCase(boxMappingPoint, DasConfigConstants.DAS_CELL_BOX_MAPPING_POINT_PREPROCESS) && ValueUtil.isEmpty(workCell.getBoxId())) {
			this.serviceDispatcher.getIndicationService(batch).displayForNoBoxError(batch, jobList.get(0).getIndCd());
			throw ThrowUtil.newValidationErrorWithNoLog(true, "CELL_HAS_NO_MAPPED_BOX");
		}
		
		// 3. 커스텀 서비스 전 처리 호출 - 커스텀 서비스에서 박스 ID를 리턴하면 리턴 박스 ID를 새로운 박스 ID로 사용
		Map<String, Object> customParams = ValueUtil.newMap("batch,workCell,jobList", batch, workCell, jobList);
		Long domainId = batch.getDomainId();
		Object objBoxId = this.customService.doCustomService(domainId, DIY_DAS_BEFORE_FULLBOX, customParams);
		String boxId = ValueUtil.isNotEmpty(objBoxId) ? objBoxId.toString() : workCell.getBoxId();
		String nowStr = DateUtil.currentTimeStr();
		
		// 4. 작업 정보 업데이트 
		for(JobInstance job : jobList) {
			job.setBoxId(boxId);
			job.setBoxedAt(nowStr);
			if(ValueUtil.isEmpty(job.getPickEndedAt())) {
				job.setPickEndedAt(nowStr);
			}
			job.setStatus(LogisConstants.JOB_STATUS_BOXED);
		}
		this.queryManager.updateBatch(jobList, "boxId", "boxedAt", "pickEndedAt", "status", "updatedAt");
		
		// 5. 박스 정보 생성
		Map<String, Object> condition = ValueUtil.newMap("domainId,batchId,boxId", domainId, batch.getId(), boxId);
		BoxPack boxPack = this.queryManager.selectByCondition(BoxPack.class, condition);
		
		// 6. 커스텀 서비스 후 처리 구현 (ex: 송장 출력, 실적 전송)
		customParams.put("box", boxPack);
		this.customService.doCustomService(domainId, DIY_DAS_AFTER_FULLBOX, customParams);
		
		// 7. WorkCell 업데이트
		if(ValueUtil.isNotEmpty(workCell.getBoxId())) {
			workCell.setBoxId(null);
			this.queryManager.update(workCell, "boxId");
		}
		
		// 8. 박스 리턴
		return boxPack;
	}

	@Override
	public BoxPack partialFullboxing(JobBatch batch, WorkCell workCell, List<JobInstance> jobList, Integer fullboxQty, Object... params) {
		throw ThrowUtil.newNotSupportedMethod();
	}

	@Override
	public List<BoxPack> batchBoxing(JobBatch batch) {
		// 1. 작업 셀 리스트 조회
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId", batch.getDomainId(), batch.getId());
		String sql = "select * from work_cells where domain_id = :domainId and batch_id = :batchId and (status is null or status != 'ENDED') and active_flag = true order by cell_cd";
		List<WorkCell> cellList = this.queryManager.selectListBySql(sql, params, WorkCell.class, 0, 0);

		// 2. 작업 셀 정보가 없으면 리턴
		if(ValueUtil.isEmpty(cellList)) {
			return new ArrayList<BoxPack>(1);
		}
		
		// 3. 박스 팩 리스트 생성
		List<BoxPack> boxPacks = new ArrayList<BoxPack>();
		
		// 4. 배치 내 박스 ID가 null이고 상태가 F(피킹 완료)인 작업 데이터를 셀 별로 모두 조회
		params.put("status", LogisConstants.JOB_STATUS_FINISH);
		sql = "select * from job_instances where domain_id = :domainId and batch_id = :batchId and status = :status and sub_equip_cd = :cellCd and (box_id is null or box_id = '') order by id";
		
		for(WorkCell cell : cellList) {
			params.put("cellCd", cell.getCellCd());
			List<JobInstance> jobList = this.queryManager.selectListBySql(sql, params, JobInstance.class, 1, 1);
			
			if(ValueUtil.isNotEmpty(jobList)) {
				// 5. 셀 별 작업이 있다면 풀 박스 처리
				JobInstance job = jobList.get(0);
				ClassifyOutEvent outEvent = new ClassifyOutEvent(SysEvent.EVENT_STEP_ALONE, Indicator.class.getSimpleName(), LogisCodeConstants.CLASSIFICATION_ACTION_FULL, job, job.getPickedQty(), job.getPickedQty());
				outEvent.setWorkCell(cell);
				this.eventPublisher.publishEvent(outEvent);
				
				if(outEvent.getResult() != null) {
					boxPacks.add((BoxPack)outEvent.getResult());
				}
			}
		}
		
		// 5. 결과 리턴 
		return boxPacks;
	}

	@Override
	public BoxPack cancelFullboxing(BoxPack box) {
		UndoBoxingEvent event = new UndoBoxingEvent(SysEvent.EVENT_STEP_ALONE, box);
		this.eventPublisher.publishEvent(event);
		return box;
	}

}
