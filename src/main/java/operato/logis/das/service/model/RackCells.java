package operato.logis.das.service.model;

/**
 * 랙 - 셀 관계 정보
 * 
 * @author shortstop
 */
public class RackCells {
	/**
	 * 호기 코드 
	 */
	private String rackCd;
	/**
	 * 호기 명 
	 */
	private String rackNm;
	/**
	 * 호기 타입
	 */
	private String rackType;
	/**
	 * 할당되지 않은 셀 개수 
	 */
	private Integer remainCells;
	/**
	 * 할당된 셀 개수
	 */
	private Integer assignedCells;
	/**
	 * 할당된 상품 개수 
	 */
	private Integer assignedSku;
	/**
	 * 할당된 개수 PCS 
	 */
	private Integer assignedPcs;
 
	/**
	 * 기본 생성자 
	 */
	public RackCells() {
	}
	
	/**
	 * 생성자
	 * 
	 * @param rackCd
	 * @param rackNm
	 * @param rackType
	 * @param remainCells
	 * @param assignedCells
	 * @param assignedSku
	 * @param assignedPcs
	 */
	public RackCells(String rackCd, String rackNm, String rackType, Integer remainCells, Integer assignedCells, Integer assignedSku, Integer assignedPcs) {
		this.rackCd = rackCd;
		this.rackNm = rackNm;
		this.rackType = rackType;
		this.remainCells = remainCells;
		this.assignedCells = assignedCells;
		this.assignedSku = assignedSku;
		this.assignedPcs = assignedPcs;
	}

	public String getRackCd() {
		return rackCd;
	}
	
	public void setRackCd(String rackCd) {
		this.rackCd = rackCd;
	}
	
	public String getRackNm() {
		return rackNm;
	}
	
	public void setRackNm(String rackNm) {
		this.rackNm = rackNm;
	}
	
	public String getRackType() {
		return rackType;
	}

	public void setRackType(String rackType) {
		this.rackType = rackType;
	}

	public Integer getRemainCells() {
		return remainCells;
	}
	
	public void setRemainCells(Integer remainCells) {
		this.remainCells = remainCells;
	}
	
	public Integer getAssignedCells() {
		return assignedCells;
	}
	
	public void setAssignedCells(Integer assignedCells) {
		this.assignedCells = assignedCells;
	}

	public Integer getAssignedSku() {
		return assignedSku;
	}

	public void setAssignedSku(Integer assignedSku) {
		this.assignedSku = assignedSku;
	}

	public Integer getAssignedPcs() {
		return assignedPcs;
	}

	public void setAssignedPcs(Integer assignedPcs) {
		this.assignedPcs = assignedPcs;
	}
}