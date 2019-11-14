package operato.logis.gtp.base.service.model;

public class RackCell {
	/**
	 * 슈트 번호
	 */
	private String chuteNo;
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
	private String regionType;
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
	public RackCell() {
	}
	
	/**
	 * 생성자
	 * 
	 * @param chuteNo
	 * @param rackCd
	 * @param rackNm
	 * @param regionType
	 * @param remainCells
	 * @param assignedCells
	 * @param assignedSku
	 * @param assignedPcs
	 */
	public RackCell(String chuteNo, String rackCd, String rackNm, String regionType, Integer remainCells, Integer assignedCells, Integer assignedSku, Integer assignedPcs) {
		this.chuteNo = chuteNo;
		this.rackCd = rackCd;
		this.rackNm = rackNm;
		this.regionType = regionType;
		this.remainCells = remainCells;
		this.assignedCells = assignedCells;
		this.assignedSku = assignedSku;
		this.assignedPcs = assignedPcs;
	}
	
	public String getChuteNo() {
		return chuteNo;
	}

	public void setChuteNo(String chuteNo) {
		this.chuteNo = chuteNo;
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
	
	public String getRegionType() {
		return regionType;
	}

	public void setRegionType(String regionType) {
		this.regionType = regionType;
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
