package es.sst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;

import org.apache.commons.lang3.StringUtils;

/**
 * This program demonstrates how to get e-mail messages from a POP3/IMAP server
 *
 * @author www.codejava.net
 *
 */
public class EmailReceiver {

	/**
	 * Returns a Properties object which is configured for a POP3/IMAP server
	 *
	 * @param protocol
	 *            either "imap" or "pop3"
	 * @param host
	 * @param port
	 * @return a Properties object
	 */
	private Properties getServerProperties(String protocol, String host, String port) {
		Properties properties = new Properties();

		// server setting
		properties.put(String.format("mail.%s.host", protocol), host);
		properties.put(String.format("mail.%s.port", protocol), port);

		// SSL setting
		properties.setProperty(String.format("mail.%s.socketFactory.class", protocol),
				"javax.net.ssl.SSLSocketFactory");
		properties.setProperty(String.format("mail.%s.socketFactory.fallback", protocol), "false");
		properties.setProperty(String.format("mail.%s.socketFactory.port", protocol), String.valueOf(port));

		return properties;
	}

	/**
	 * Downloads new messages and fetches details for each message.
	 *
	 * @param protocol
	 * @param host
	 * @param port
	 * @param userName
	 * @param password
	 */
	public void downloadEmails(String protocol, String host, String port, String userName, String password) {
		Properties properties = getServerProperties(protocol, host, port);
		Session session = Session.getDefaultInstance(properties);

		try {
			// connects to the message store
			Store store = session.getStore(protocol);
			store.connect(userName, password);

			// opens the inbox folder
			Folder folderInbox = store.getFolder("INBOX");
			folderInbox.open(Folder.READ_ONLY);

			Folder defaultFolder = store.getDefaultFolder();
			System.out.println("default folder:" + defaultFolder.getFullName());

			Folder[] foldersArray = defaultFolder.list();

			List<Folder> folders = Arrays.asList(foldersArray);

			Folder inboxFolder = null;
			for (Folder folder : folders) {
				String name = StringUtils.trimToEmpty(folder.getFullName());
				if (StringUtils.containsIgnoreCase(name, "inbox")) {
					System.out.println("inbox folder:" + name);
					inboxFolder = folder;
				}

			}

			if (inboxFolder != null) {
				List<Folder> foldersToDownload = getAllSubFolders(inboxFolder);
				foldersToDownload.add(inboxFolder);
				System.out.println("folders recuperados:" + foldersToDownload.size());

				Map<String, List<Object>> invoiceMessagesMap = new HashMap<String, List<Object>>();
				for (Folder folder : foldersToDownload) {
					System.out.println(folder.getFullName());
					Map<String, List<Object>> invoiceMessagesFolderMap = getInvoiceMessagesFromFolder(folder);

				}

			}

			store.close();

			// if(inboxFolder!=null){
			// System.out.println("inboxEncontrado"+inboxFolder.getFullName());
			// Folder[] inboxFoldersArray = inboxFolder.list();
			//
			// List<Folder> inboxFolders = Arrays.asList(inboxFoldersArray);
			//
			// for (Folder folder : inboxFolders) {
			// System.out.println("inboxInner-->"+folder.getFullName());
			// }
			//
			// }
			if (true)
				return;

			// fetches new messages from server
			Message[] messages = folderInbox.getMessages();

			for (int i = 0; i < messages.length; i++) {
				Message msg = messages[i];
				Address[] fromAddress = msg.getFrom();
				String from = fromAddress[0].toString();
				String subject = msg.getSubject();
				String toList = parseAddresses(msg.getRecipients(RecipientType.TO));
				String ccList = parseAddresses(msg.getRecipients(RecipientType.CC));
				String sentDate = msg.getSentDate().toString();

				String contentType = msg.getContentType();
				String messageContent = "";

				if (contentType.contains("text/plain") || contentType.contains("text/html")) {
					try {
						Object content = msg.getContent();
						if (content != null) {
							messageContent = content.toString();
						}
					} catch (Exception ex) {
						messageContent = "[Error downloading content]";
						ex.printStackTrace();
					}
				}

				// print out details of each message
				System.out.println("Message #" + (i + 1) + ":");
				System.out.println("\t From: " + from);
				System.out.println("\t To: " + toList);
				System.out.println("\t CC: " + ccList);
				System.out.println("\t Subject: " + subject);
				System.out.println("\t Sent Date: " + sentDate);
				System.out.println("\t Message: " + messageContent);
			}

			// disconnect
			folderInbox.close(false);
			store.close();
		} catch (NoSuchProviderException ex) {
			System.out.println("No provider for protocol: " + protocol);
			ex.printStackTrace();
		} catch (MessagingException ex) {
			System.out.println("Could not connect to the message store");
			ex.printStackTrace();
		}
	}

	private Map<String, List<Object>> getInvoiceMessagesFromFolder(Folder folder) {
		Map<String, List<Object>> resultado = new HashMap<String, List<Object>>();
		try {
			if (!(folder instanceof UIDFolder)) {
				System.out.println("This Provider or this folder does not support UIDs");
				return resultado;
			}

			UIDFolder ufolder = (UIDFolder) folder;

			folder.open(Folder.READ_WRITE);

			int totalMessages = folder.getMessageCount();

			if (totalMessages == 0) {
				System.out.println("Empty folder");
				folder.close(false);
				return resultado;
			}

			// Attributes & Flags for ALL messages ..
			Message[] msgs = ufolder.getMessagesByUID(1, UIDFolder.LASTUID);
			// Use a suitable FetchProfile
			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add(FetchProfile.Item.FLAGS);
			folder.fetch(msgs, fp);

			for (int i = msgs.length-1; i >=0; i--) {
				System.out.println("--------------------------");
				System.out.println("MESSAGE UID #" + ufolder.getUID(msgs[i]) + ":"+msgs[i].getSubject()+"_"+msgs[i].getFolder());
				// dumpPart(msgs[i]);
			}

			folder.close(false);
			return resultado;
		} catch (MessagingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return resultado;
		}
	}

	private List<Folder> getAllSubFolders(Folder folder) {
		try {
			List<Folder> resultado = new ArrayList<Folder>();

			Folder[] foldersArray = folder.list();
			List<Folder> subFolders = Arrays.asList(foldersArray);
			if (!subFolders.isEmpty()) {
				resultado.addAll(Arrays.asList(foldersArray));
				for (Folder subFolder : subFolders) {
					List<Folder> subSubFolders = getAllSubFolders(subFolder);
					if (!subSubFolders.isEmpty()) {
						resultado.addAll(subSubFolders);
					}
				}

				return resultado;
			} else {
				return resultado;
			}

		} catch (MessagingException e) {
			e.printStackTrace();
			return null;

		}

	}

	/**
	 * Returns a list of addresses in String format separated by comma
	 *
	 * @param address
	 *            an array of Address objects
	 * @return a string represents a list of addresses
	 */
	private String parseAddresses(Address[] address) {
		String listAddress = "";

		if (address != null) {
			for (int i = 0; i < address.length; i++) {
				listAddress += address[i].toString() + ", ";
			}
		}
		if (listAddress.length() > 1) {
			listAddress = listAddress.substring(0, listAddress.length() - 2);
		}

		return listAddress;
	}

	/**
	 * Test downloading e-mail messages
	 */
	public static void main(String[] args) {
		// for POP3
		// String protocol = "pop3";
		// String host = "pop.gmail.com";
		// String port = "995";

		// for IMAP
		// String protocol = "imap";
		// String host = "imap.gmail.com";
		// String port = "993";
		//
		// for IMAP
		String protocol = "imap";
		String host = "imap-mail.outlook.com";
		String port = "993";

		String userName = "sebassalazart@hotmail.com";
		String password = "A1a2a3a4a5";

		EmailReceiver receiver = new EmailReceiver();
		receiver.downloadEmails(protocol, host, port, userName, password);
	}
}