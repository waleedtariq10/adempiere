/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

import org.adempiere.engine.StorageEngine;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.process.DocAction;
import org.compiere.process.DocumentEngine;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.eevolution.model.MDDOrder;

/**
 *	Inventory Movement Model
 *	
 *  @author Jorg Janke
 *  @author victor.perez@e-evolution.com, e-Evolution http://www.e-evolution.com
 * 			<li>FR [ 1948157  ]  Is necessary the reference for document reverse
 *  		@see http://sourceforge.net/tracker/?func=detail&atid=879335&aid=1948157&group_id=176962
 * 			<li> FR [ 2520591 ] Support multiples calendar for Org 
 *			@see http://sourceforge.net/tracker2/?func=detail&atid=879335&aid=2520591&group_id=176962 
 *  @author Armen Rizal, Goodwill Consulting
 * 			<li>BF [ 1745154 ] Cost in Reversing Material Related Docs  
 *  @author Teo Sarca, www.arhipac.ro
 *  		<li>FR [ 2214883 ] Remove SQL code and Replace for Query
 *  @version $Id: MMovement.java,v 1.3 2006/07/30 00:51:03 jjanke Exp $
 */
public class MMovement extends X_M_Movement implements DocAction
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1628932946440487727L;

	/**
	 * 	Standard Constructor
	 *	@param ctx context
	 *	@param M_Movement_ID id
	 *	@param trxName transaction
	 */
	public MMovement (Properties ctx, int M_Movement_ID, String trxName)
	{
		super (ctx, M_Movement_ID, trxName);
		if (M_Movement_ID == 0)
		{
		//	setC_DocType_ID (0);
			setDocAction (DOCACTION_Complete);	// CO
			setDocStatus (DOCSTATUS_Drafted);	// DR
			setIsApproved (false);
			setIsInTransit (false);
			setMovementDate (new Timestamp(System.currentTimeMillis()));	// @#Date@
			setPosted (false);
			super.setProcessed (false);
		}	
	}	//	MMovement

	/**
	 * 	Load Constructor
	 *	@param ctx context
	 *	@param rs result set
	 *	@param trxName transaction
	 */
	public MMovement (Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}	//	MMovement

	/**
	 * Created an instance based Distribution Order
	 * @param order
	 * @param movementDate
     */
	public MMovement(MDDOrder order , Timestamp movementDate)
	{
		super(order.getCtx() , 0 , order.get_TrxName());
		setDD_Order_ID(order.getDD_Order_ID());
		setAD_User_ID(order.getAD_User_ID());
		setPOReference(order.getPOReference());
		setReversal_ID(0);
		setM_Shipper_ID(order.getM_Shipper_ID());
		setDescription(order.getDescription());
		setC_BPartner_ID(order.getC_BPartner_ID());
		setC_BPartner_Location_ID(order.getC_BPartner_Location_ID());
		setAD_Org_ID(order.getAD_Org_ID());
		setAD_OrgTrx_ID(order.getAD_OrgTrx_ID());
		setAD_User_ID(order.getAD_User_ID());
		setC_Activity_ID(order.getC_Activity_ID());
		setC_Charge_ID(order.getC_Charge_ID());
		setChargeAmt(order.getChargeAmt());
		setC_Campaign_ID(order.getC_Campaign_ID());
		setC_Project_ID(order.getC_Project_ID());
		setAD_OrgTrx_ID(order.getAD_OrgTrx_ID());
		setUser1_ID(order.getUser1_ID());
		setUser2_ID(order.getUser2_ID());
		setPriorityRule(order.getPriorityRule());
		if (movementDate != null)
			setMovementDate (movementDate);
		setDeliveryRule(order.getDeliveryRule());
		setDeliveryViaRule(order.getDeliveryViaRule());
		setDocStatus(MMovement.DOCSTATUS_Drafted);
		setDocAction(MMovement.ACTION_Prepare);
		setFreightCostRule (order.getFreightCostRule());
		setFreightAmt(order.getFreightAmt());
		setSalesRep_ID(order.getSalesRep_ID());
	}

	/**	Lines						*/
	private MMovementLine[]		m_lines = null;
	/** Confirmations				*/
	private MMovementConfirm[]	m_confirms = null;
	/** Reversal Indicator			*/
	public static String	REVERSE_INDICATOR = "^";
	
	/**
	 * 	Get Lines
	 *	@param requery requery
	 *	@return array of lines
	 */
	public MMovementLine[] getLines (boolean requery)
	{
		if (m_lines != null && !requery) {
			set_TrxName(m_lines, get_TrxName());
			return m_lines;
		}
		//
		final String whereClause = "M_Movement_ID=?";
		List<MMovementLine> list = new Query(getCtx(), I_M_MovementLine.Table_Name, whereClause, get_TrxName())
		 										.setParameters(getM_Movement_ID())
		 										.setOrderBy(MMovementLine.COLUMNNAME_Line)
		 										.list();
		m_lines = new MMovementLine[list.size ()];
		list.toArray (m_lines);
		return m_lines;
	}	//	getLines

	/**
	 * 	Get Confirmations
	 * 	@param requery requery
	 *	@return array of Confirmations
	 */
	public MMovementConfirm[] getConfirmations(boolean requery)
	{
		if (m_confirms != null && !requery)
			return m_confirms;

		List<MMovementConfirm> list = new Query(getCtx(), I_M_MovementConfirm.Table_Name, "M_Movement_ID=?", get_TrxName())
										.setParameters(get_ID())
										.list();
		m_confirms = list.toArray(new MMovementConfirm[list.size()]);
		return m_confirms;
	}	//	getConfirmations

	/**
	 * 	Add to Description
	 *	@param description text
	 */
	public void addDescription (String description)
	{
		String desc = getDescription();
		if (desc == null)
			setDescription(description);
		else
			setDescription(desc + " | " + description);
	}	//	addDescription
	
	/**
	 * 	Get Document Info
	 *	@return document info (untranslated)
	 */
	public String getDocumentInfo()
	{
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		return dt.getName() + " " + getDocumentNo();
	}	//	getDocumentInfo

	/**
	 * 	Create PDF
	 *	@return File or null
	 */
	public File createPDF ()
	{
		try
		{
			File temp = File.createTempFile(get_TableName()+get_ID()+"_", ".pdf");
			return createPDF (temp);
		}
		catch (Exception e)
		{
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}	//	getPDF

	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF (File file)
	{
	//	ReportEngine re = ReportEngine.get (getCtx(), ReportEngine.INVOICE, getC_Invoice_ID());
	//	if (re == null)
			return null;
	//	return re.getPDF(file);
	}	//	createPDF

	
	/**
	 * 	Before Save
	 *	@param newRecord new
	 *	@return true
	 */
	protected boolean beforeSave (boolean newRecord)
	{
		if (getC_DocType_ID() == 0)
		{
			MDocType types[] = MDocType.getOfDocBaseType(getCtx(), MDocType.DOCBASETYPE_MaterialMovement);
			if (types.length > 0)	//	get first
				setC_DocType_ID(types[0].getC_DocType_ID());
			else
			{
				log.saveError("Error", Msg.parseTranslation(getCtx(), "@NotFound@ @C_DocType_ID@"));
				return false;
			}
		}
		return true;
	}	//	beforeSave
	
	/**
	 * 	Set Processed.
	 * 	Propergate to Lines/Taxes
	 *	@param processed processed
	 */
	@Override
	public void setProcessed (boolean processed)
	{
		super.setProcessed (processed);
		if (get_ID() == 0)
			return;
		final String sql = "UPDATE M_MovementLine SET Processed=? WHERE M_Movement_ID=?";
		int noLine = DB.executeUpdateEx(sql, new Object[]{processed, get_ID()}, get_TrxName());
		m_lines = null;
		log.fine("Processed=" + processed + " - Lines=" + noLine);
	}	//	setProcessed
	
	
	/**************************************************************************
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;

	/**
	 * 	Unlock Document.
	 * 	@return true if success 
	 */
	public boolean unlockIt()
	{
		log.info(toString());
		setProcessing(false);
		return true;
	}	//	unlockIt
	
	/**
	 * 	Invalidate Document
	 * 	@return true if success 
	 */
	public boolean invalidateIt()
	{
		log.info(toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}	//	invalidateIt
	
	/**
	 *	Prepare Document
	 * 	@return new status (In Progress or Invalid) 
	 */
	public String prepareIt()
	{
		log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());

		//	Std Period open?
		if (!MPeriod.isOpen(getCtx(), getMovementDate(), dt.getDocBaseType(), getAD_Org_ID()))
		{
			m_processMsg = "@PeriodClosed@";
			return DocAction.STATUS_Invalid;
		}
		MMovementLine[] lines = getLines(false);
		if (lines.length == 0)
		{
			m_processMsg = "@NoLines@";
			return DocAction.STATUS_Invalid;
		}
		
		//	Confirmation
		if (dt.isInTransit())
			createConfirmation();

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}	//	prepareIt
	
	/**
	 * 	Create Movement Confirmation
	 */
	private void createConfirmation()
	{
		MMovementConfirm[] confirmations = getConfirmations(false);
		if (confirmations.length > 0)
			return;
		
		//	Create Confirmation
		MMovementConfirm.create (this, false);
	}	//	createConfirmation
	
	/**
	 * 	Approve Document
	 * 	@return true if success 
	 */
	public boolean  approveIt()
	{
		log.info(toString());
		setIsApproved(true);
		return true;
	}	//	approveIt
	
	/**
	 * 	Reject Approval
	 * 	@return true if success 
	 */
	public boolean rejectIt()
	{
		log.info(toString());
		setIsApproved(false);
		return true;
	}	//	rejectIt
	
	/**
	 * 	Complete Document
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}
		
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;

		//	Outstanding (not processed) Incoming Confirmations ?
		MMovementConfirm[] confirmations = getConfirmations(true);
		for (int i = 0; i < confirmations.length; i++)
		{
			MMovementConfirm confirm = confirmations[i];
			if (!confirm.isProcessed())
			{
				m_processMsg = "Open: @M_MovementConfirm_ID@ - " 
					+ confirm.getDocumentNo();
				return DocAction.STATUS_InProgress;
			}
		}
		
		//	Implicit Approval
		if (!isApproved())
			approveIt();
		log.info(toString());
		
		//
		MMovementLine[] lines = getLines(false);
		for (int i = 0; i < lines.length; i++)
		{
			MMovementLine line = lines[i];
			
			//Stock Movement
			MLocator locator = new MLocator (getCtx(), line.getM_Locator_ID(), get_TrxName());
			MLocator locatorTo = new MLocator (getCtx(), line.getM_LocatorTo_ID(), get_TrxName());

			// Move from
			try {
				StorageEngine.createTransaction(
						line,
						MTransaction.MOVEMENTTYPE_MovementFrom, 
						getMovementDate() , 
						line.getMovementQty(), 
						isReversal(),							
						locator.getM_Warehouse_ID(), 
						0,								// Reservation ASI - not relevant
						0,								// Reservation Warehouse  - not relevant
						false							// IsSOTrx=false
						);
			}
			catch (AdempiereException e) {
				m_processMsg = e.getMessage();
				return DocAction.STATUS_Invalid;
			}

			// Move to
			try {
				StorageEngine.createTransaction(
						line,
						MTransaction.MOVEMENTTYPE_MovementTo, 
						getMovementDate() , 
						line.getMovementQty(), 
						isReversal(),							
						locatorTo.getM_Warehouse_ID(), 
						0,								// Ordered ASI - not relevant
						0, 								// Ordered Warehouse - not relevant
						false,							// IsSOTrx=false
						false							// Important! - don't delete existing MA entries
						);
			}
			catch (AdempiereException e) {
				m_processMsg = e.getMessage();
				return DocAction.STATUS_Invalid;
			}

			log.fine("Material Transaction Complete.");

		}	//	for all lines
		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
		
		// Set the definite document number after completed (if needed)
		setDefiniteDocumentNo();

		//
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		return DocAction.STATUS_Completed;
	}	//	completeIt
	
	/**
	 * 	Set the definite document number after completed
	 */
	private void setDefiniteDocumentNo() {
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		if (dt.isOverwriteDateOnComplete()) {
			setMovementDate(new Timestamp (System.currentTimeMillis()));
		}
		if (dt.isOverwriteSeqOnComplete()) {
			String value = DB.getDocumentNo(getC_DocType_ID(), get_TrxName(), true, this);
			if (value != null)
				setDocumentNo(value);
		}
	}

	/**
	 * 	Void Document.
	 * 	@return true if success 
	 */
	public boolean voidIt()
	{
		log.info(toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;
		
		if (DOCSTATUS_Closed.equals(getDocStatus())
			|| DOCSTATUS_Reversed.equals(getDocStatus())
			|| DOCSTATUS_Voided.equals(getDocStatus()))
		{
			m_processMsg = "Document Closed: " + getDocStatus();
			return false;
		}

		//	Not Processed
		if (DOCSTATUS_Drafted.equals(getDocStatus())
			|| DOCSTATUS_Invalid.equals(getDocStatus())
			|| DOCSTATUS_InProgress.equals(getDocStatus())
			|| DOCSTATUS_Approved.equals(getDocStatus())
			|| DOCSTATUS_NotApproved.equals(getDocStatus()) )
		{
			//	Set lines to 0
			MMovementLine[] lines = getLines(false);
			for (int i = 0; i < lines.length; i++)
			{
				MMovementLine line = lines[i];
				BigDecimal old = line.getMovementQty();
				if (old.compareTo(Env.ZERO) != 0)
				{
					line.setMovementQty(Env.ZERO);
					line.addDescription("Void (" + old + ")");
					line.saveEx();
				}
			}
		}
		else
		{
			return reverseCorrectIt();
		}
		// After Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;
			
		setProcessed(true);
		setDocAction(DOCACTION_None);
		return true;
	}	//	voidIt
	
	/**
	 * 	Close Document.
	 * 	@return true if success 
	 */
	public boolean closeIt()
	{
		log.info(toString());
		// Before Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_CLOSE);
		if (m_processMsg != null)
			return false;

		//	Close Not delivered Qty
		setDocAction(DOCACTION_None);

		// After Close
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_CLOSE);
		if (m_processMsg != null)
			return false;
		return true;
	}	//	closeIt
	
	/**
	 * 	Reverse Correction
	 * 	@return false 
	 */
	public boolean reverseCorrectIt()
	{
		log.info(toString());
		// Before reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		if (!MPeriod.isOpen(getCtx(), getMovementDate(), dt.getDocBaseType(), getAD_Org_ID()))
		{
			m_processMsg = "@PeriodClosed@";
			return false;
		}

		//	Deep Copy
		MMovement reversal = new MMovement(getCtx(), 0, get_TrxName());
		copyValues(this, reversal, getAD_Client_ID(), getAD_Org_ID());
		reversal.setDocStatus(DOCSTATUS_Drafted);
		reversal.setDocAction(DOCACTION_Complete);
		reversal.setIsApproved (false);
		reversal.setIsInTransit (false);
		reversal.setPosted(false);
		reversal.setProcessed(false);
		reversal.setDocumentNo(getDocumentNo() + REVERSE_INDICATOR);	//	indicate reversals
		reversal.addDescription("{->" + getDocumentNo() + ")");
		//FR [ 1948157  ]
		reversal.setReversal_ID(getM_Movement_ID());
		if (!reversal.save())
		{
			m_processMsg = "Could not create Movement Reversal";
			return false;
		}
		reversal.setReversal(true);
		//	Reverse Line Qty
		MMovementLine[] oLines = getLines(true);
		for (int i = 0; i < oLines.length; i++)
		{
			MMovementLine oLine = oLines[i];
			MMovementLine rLine = new MMovementLine(getCtx(), 0, get_TrxName());
			copyValues(oLine, rLine, oLine.getAD_Client_ID(), oLine.getAD_Org_ID());
			rLine.setM_Movement_ID(reversal.getM_Movement_ID());
			//AZ Goodwill			
			// store original (voided/reversed) document line
			rLine.setReversalLine_ID(oLine.getM_MovementLine_ID());
			//
			rLine.setMovementQty(rLine.getMovementQty().negate());
			rLine.setTargetQty(Env.ZERO);
			rLine.setScrappedQty(Env.ZERO);
			rLine.setConfirmedQty(Env.ZERO);
			rLine.setProcessed(false);
			rLine.saveEx();
		}
		//
		if (!reversal.processIt(DocAction.ACTION_Complete))
		{
			m_processMsg = "Reversal ERROR: " + reversal.getProcessMsg();
			return false;
		}
		reversal.closeIt();
		reversal.setDocStatus(DOCSTATUS_Reversed);
		reversal.setDocAction(DOCACTION_None);
		reversal.saveEx();
		m_processMsg = reversal.getDocumentNo();
		
		// After reverseCorrect
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSECORRECT);
		if (m_processMsg != null)
			return false;
		
		//	Update Reversed (this)
		addDescription("(" + reversal.getDocumentNo() + "<-)");
		//FR [ 1948157  ]
		setReversal_ID(reversal.getM_Movement_ID());
		setProcessed(true);
		setDocStatus(DOCSTATUS_Reversed);	//	may come from void
		setDocAction(DOCACTION_None);
		
		return true;
	}	//	reverseCorrectionIt
	
	/**
	 * 	Reverse Accrual - none
	 * 	@return false 
	 */
	public boolean reverseAccrualIt()
	{
		log.info(toString());
		// Before reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		// After reverseAccrual
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REVERSEACCRUAL);
		if (m_processMsg != null)
			return false;
		
		return false;
	}	//	reverseAccrualIt
	
	/** 
	 * 	Re-activate
	 * 	@return false 
	 */
	public boolean reActivateIt()
	{
		log.info(toString());
		// Before reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;	
		
		// After reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REACTIVATE);
		if (m_processMsg != null)
			return false;
		
		return false;
	}	//	reActivateIt
	
	
	/*************************************************************************
	 * 	Get Summary
	 *	@return Summary of Document
	 */
	public String getSummary()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(getDocumentNo());
		//	: Total Lines = 123.00 (#1)
		sb.append(": ")
			.append(Msg.translate(getCtx(),"ApprovalAmt")).append("=").append(getApprovalAmt())
			.append(" (#").append(getLines(false).length).append(")");
		//	 - Description
		if (getDescription() != null && getDescription().length() > 0)
			sb.append(" - ").append(getDescription());
		return sb.toString();
	}	//	getSummary
	
	/**
	 * 	Get Process Message
	 *	@return clear text error message
	 */
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg
	
	/**
	 * 	Get Document Owner (Responsible)
	 *	@return AD_User_ID
	 */
	public int getDoc_User_ID()
	{
		return getCreatedBy();
	}	//	getDoc_User_ID

	/**
	 * 	Get Document Currency
	 *	@return C_Currency_ID
	 */
	public int getC_Currency_ID()
	{
	//	MPriceList pl = MPriceList.get(getCtx(), getM_PriceList_ID());
	//	return pl.getC_Currency_ID();
		return 0;
	}	//	getC_Currency_ID
	
	/** Reversal Flag		*/
	private boolean m_reversal = false;
	
	/**
	 * 	Set Reversal
	 *	@param reversal reversal
	 */
	private void setReversal(boolean reversal)
	{
		m_reversal = reversal;
	}	//	setReversal
	/**
	 * 	Is Reversal
	 *	@return reversal
	 */
	private boolean isReversal()
	{
		return m_reversal;
	}	//	isReversal

	/**
	 * 	Document Status is Complete or Closed
	 *	@return true if CO, CL or RE
	 */
	public boolean isComplete()
	{
		String ds = getDocStatus();
		return DOCSTATUS_Completed.equals(ds) 
			|| DOCSTATUS_Closed.equals(ds)
			|| DOCSTATUS_Reversed.equals(ds);
	}	//	isComplete
	
	private boolean isSameCostDimension(MAcctSchema as, MTransaction trxFrom, MTransaction trxTo)
	{
		if (trxFrom.getM_Product_ID() != trxTo.getM_Product_ID())
		{
			throw new AdempiereException("Same product is needed - "+trxFrom+", "+trxTo);
		}
		MProduct product = MProduct.get(getCtx(), trxFrom.getM_Product_ID());
		String CostingLevel = product.getCostingLevel(as,trxFrom.getAD_Org_ID());
		int Org_ID = trxFrom.getAD_Org_ID();
		int Org_ID_To = trxTo.getAD_Org_ID();
		int ASI_ID = trxFrom.getM_AttributeSetInstance_ID();
		int ASI_ID_To = trxTo.getM_AttributeSetInstance_ID();
		if (MAcctSchema.COSTINGLEVEL_Client.equals(CostingLevel))
		{
			Org_ID = 0;
			Org_ID_To = 0;
			ASI_ID = 0;
			ASI_ID_To = 0;
		}
		else if (MAcctSchema.COSTINGLEVEL_Organization.equals(CostingLevel))
		{
			ASI_ID = 0;
			ASI_ID_To = 0;
		}
		else if (MAcctSchema.COSTINGLEVEL_BatchLot.equals(CostingLevel))
		{
			Org_ID = 0;
			Org_ID_To = 0;
		}
		//
		return Org_ID == Org_ID_To && ASI_ID == ASI_ID_To;
	}
}	//	MMovement

