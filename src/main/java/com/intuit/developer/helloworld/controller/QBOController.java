package com.intuit.developer.helloworld.controller;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.developer.helloworld.client.OAuth2PlatformClientFactory;
import com.intuit.developer.helloworld.controller.customers.VendCustomersController;
import com.intuit.developer.helloworld.helper.QBOServiceHelper;
import com.intuit.ipp.core.IEntity;
import com.intuit.ipp.data.CompanyInfo;
import com.intuit.ipp.data.Customer;
import com.intuit.ipp.data.EmailAddress;
import com.intuit.ipp.data.Error;
import com.intuit.ipp.exception.FMSException;
import com.intuit.ipp.exception.InvalidTokenException;
import com.intuit.ipp.services.DataService;
import com.intuit.ipp.services.QueryResult;
import com.intuit.oauth2.client.OAuth2PlatformClient;
import com.intuit.oauth2.data.BearerTokenResponse;
import com.intuit.oauth2.exception.OAuthException;
import com.vendhq.api.ApiException;
import com.vendhq.models.CustomerCollection;

/**
 * @author dderose
 *
 */
@Controller
public class QBOController {

	@Autowired
	OAuth2PlatformClientFactory factory;

	@Autowired
	public QBOServiceHelper helper;

	@Autowired
	public VendCustomersController customersController;

	private static final Logger logger = Logger.getLogger(QBOController.class);
	private static final String failureMsg = "Failed";

	/**
	 * Synchronize customer information from Vend into QBO.
	 * 
	 * When creating a QBO Customer it only creates a basic customer.
	 * 
	 * @param session
	 * @return
	 * @throws ApiException
	 * @throws FMSException
	 */
	@ResponseBody
	@RequestMapping("/syncCustomers")
	public String syncCustomerFromVendHQ(HttpSession session) throws ApiException, FMSException {

		String realmId = (String) session.getAttribute("realmId");
		List<Customer> existingQBOCustomers = null;
		List<IEntity> newlySyncedCustomers = new ArrayList<>();
		if (StringUtils.isEmpty(realmId)) {
			return new JSONObject()
					.put("response", "No realm ID.  QBO calls only work if the accounting scope was passed!")
					.toString();
		}
		String accessToken = (String) session.getAttribute("access_token");

		// Retrieve customers from Vend
		CustomerCollection customersFromVend = customersController.getCustomers();

		// Retrieve customers from QuickBooks
		try {
			DataService service = helper.getDataService(realmId, accessToken);
			String sql = "select * from customer";
			QueryResult queryResult = service.executeQuery(sql);
			existingQBOCustomers = (List<Customer>) queryResult.getEntities();
			logger.debug("Total customers retrieved from QBO " + existingQBOCustomers.size());

			// return processResponse(failureMsg, queryResult);

			for (com.vendhq.models.Customer vendCustomer : customersFromVend.getData()) {
				// For each non existing vend customers create them in QBO.
				if (StringUtils.isNotEmpty(vendCustomer.getFirstName())
						&& !existingQBOCustomer(vendCustomer, existingQBOCustomers)) {

					IEntity newQBOCustomer = convertToQBOCustomer(vendCustomer);
					service.add(newQBOCustomer);
					logger.debug("Creating a new customer in QBO " + vendCustomer);
					newlySyncedCustomers.add(newQBOCustomer);
				}
			}

			// For missing customers create a new customer in QuickBook
		} catch (FMSException e) {
			e.printStackTrace();
			throw e;
		}
		
		logger.debug("Newly synced customers "+ newlySyncedCustomers);
		
		return "Number of new customers synchronised "+ newlySyncedCustomers.size();
		//return "Total customers from QBO " + existingQBOCustomers.size() + "\n" + customersFromVend.toString();
	}

	private IEntity convertToQBOCustomer(com.vendhq.models.Customer vendCustomer) {

		Customer newQBOCustomer = new Customer();
		newQBOCustomer.setOrganization(Boolean.FALSE);
		newQBOCustomer.setCompanyName(vendCustomer.getCompanyName());
		newQBOCustomer.setActive(Boolean.TRUE);
		newQBOCustomer.setDisplayName(vendCustomer.getName());
		newQBOCustomer.setFamilyName(vendCustomer.getLastName());
		newQBOCustomer.setGivenName(vendCustomer.getFirstName());
		EmailAddress emailAddr = new EmailAddress();
		emailAddr.setAddress(vendCustomer.getEmail());
		newQBOCustomer.setPrimaryEmailAddr(emailAddr);
		return newQBOCustomer;
	}

	private boolean existingQBOCustomer(com.vendhq.models.Customer vendCustomer, List<Customer> existingQBOCustomers) {

		for (Customer existingQBOCustomer : existingQBOCustomers) {
			if (existingQBOCustomer.getCompanyName() != null
					&& existingQBOCustomer.getCompanyName().equals(vendCustomer.getCompanyName())
					&& existingQBOCustomer.getDisplayName() != null
					&& existingQBOCustomer.getDisplayName().equals(vendCustomer.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sample QBO API call using OAuth2 tokens
	 * 
	 * @param session
	 * @return
	 */
	@ResponseBody
	@RequestMapping("/getCompanyInfo")
	public String callQBOCompanyInfo(HttpSession session) {

		String realmId = (String) session.getAttribute("realmId");
		if (StringUtils.isEmpty(realmId)) {
			return new JSONObject()
					.put("response", "No realm ID.  QBO calls only work if the accounting scope was passed!")
					.toString();
		}
		String accessToken = (String) session.getAttribute("access_token");

		try {

			// get DataService
			DataService service = helper.getDataService(realmId, accessToken);

			// get all companyinfo
			String sql = "select * from companyinfo";
			QueryResult queryResult = service.executeQuery(sql);
			return processResponse(failureMsg, queryResult);

		}
		/*
		 * Handle 401 status code - If a 401 response is received, refresh tokens should
		 * be used to get a new access token, and the API call should be tried again.
		 */
		catch (InvalidTokenException e) {
			logger.error("Error while calling executeQuery :: " + e.getMessage());

			// refresh tokens
			logger.info("received 401 during companyinfo call, refreshing tokens now");
			OAuth2PlatformClient client = factory.getOAuth2PlatformClient();
			String refreshToken = (String) session.getAttribute("refresh_token");

			try {
				BearerTokenResponse bearerTokenResponse = client.refreshToken(refreshToken);
				session.setAttribute("access_token", bearerTokenResponse.getAccessToken());
				session.setAttribute("refresh_token", bearerTokenResponse.getRefreshToken());

				// call company info again using new tokens
				logger.info("calling companyinfo using new tokens");
				DataService service = helper.getDataService(realmId, accessToken);

				// get all companyinfo
				String sql = "select * from companyinfo";
				QueryResult queryResult = service.executeQuery(sql);
				return processResponse(failureMsg, queryResult);

			} catch (OAuthException e1) {
				logger.error("Error while calling bearer token :: " + e.getMessage());
				return new JSONObject().put("response", failureMsg).toString();
			} catch (FMSException e1) {
				logger.error("Error while calling company currency :: " + e.getMessage());
				return new JSONObject().put("response", failureMsg).toString();
			}

		} catch (FMSException e) {
			List<Error> list = e.getErrorList();
			list.forEach(error -> logger.error("Error while calling executeQuery :: " + error.getMessage()));
			return new JSONObject().put("response", failureMsg).toString();
		}

	}

	private String processResponse(String failureMsg, QueryResult queryResult) {
		if (!queryResult.getEntities().isEmpty() && queryResult.getEntities().size() > 0) {
			CompanyInfo companyInfo = (CompanyInfo) queryResult.getEntities().get(0);
			logger.info("Companyinfo -> CompanyName: " + companyInfo.getCompanyName());
			ObjectMapper mapper = new ObjectMapper();
			try {
				String jsonInString = mapper.writeValueAsString(companyInfo);
				return jsonInString;
			} catch (JsonProcessingException e) {
				logger.error("Exception while getting company info ", e);
				return new JSONObject().put("response", failureMsg).toString();
			}

		}
		return failureMsg;
	}

}
