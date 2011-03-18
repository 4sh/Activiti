/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.engine.impl.mail;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import org.activiti.engine.impl.db.DbSqlSession;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.runtime.ByteArrayEntity;
import org.activiti.engine.impl.task.AttachmentEntity;
import org.activiti.engine.impl.task.TaskEntity;


/**
 * @author Tom Baeyens
 */
public class MailScanCmd implements Command<Object> {
  
  private static Logger log = Logger.getLogger(MailScanCmd.class.getName());

  protected String imapUsername = "tombaeyens3@gmail.com";
  protected String imapPassword = System.getProperty("pwd");
  protected String host = "imap.gmail.com";
  protected String protocol = "imaps";
  protected String toDoFolderName = "MyToDos";
  protected String toDoInActivitiFolderName = "MyToDosInActiviti";
  
  public MailScanCmd(String imapUsername, String imapPassword) {
    this.imapUsername = imapUsername;
  }

  public Object execute(CommandContext commandContext) {
    Store store = null;
    Folder toDoFolder = null;
    Folder toDoInActiviti = null;
    
    try {

      Session session = Session.getDefaultInstance(new Properties());
      store = session.getStore(protocol);
      log.fine("connecting to "+host+" over "+protocol+" for user "+imapUsername);
      store.connect(host, imapUsername, imapPassword);

      toDoFolder = store.getFolder(toDoFolderName);
      toDoFolder.open(Folder.READ_WRITE);
      toDoInActiviti = store.getFolder(toDoInActivitiFolderName);
      toDoInActiviti.open(Folder.READ_WRITE);
      
      Message[] messages = toDoFolder.getMessages();
      log.fine("getting messages from myToDoFolder");

      DbSqlSession dbSqlSession = commandContext.getDbSqlSession();
      TaskEntity task = new TaskEntity();
      task.setAssignee(imapUsername);
      

      for (Message message: messages) {
        log.fine("transforming mail into activiti task: "+message.getSubject());
        MailTransformer messageTransformer = new MailTransformer(message);
        
        List<AttachmentEntity> attachments = messageTransformer.getAttachments();
        for (AttachmentEntity attachment: attachments) {
          ByteArrayEntity content = attachment.getContent();
          dbSqlSession.insert(content);
          
          // TODO store attachment and attachment.getContent()
          attachment.setContentId(content.getId());
          dbSqlSession.insert(attachment);
        }

        // Message[] messagesToCopy = new Message[]{message};
        // myToDos.copyMessages(messagesToCopy, myToDosInActiviti);
        // message.setFlag(Flags.Flag.DELETED, true);
      }

      toDoInActiviti.close(false);
      
    } catch (Exception e) {
      e.printStackTrace();
      
    } finally {
      if (toDoInActiviti!=null) {
        try {
          toDoInActiviti.close(false);
        } catch (MessagingException e) {
          e.printStackTrace();
        }
      }
      if (toDoFolder!=null) {
        try {
          toDoFolder.close(true); // true means that all messages that are flagged for deletion are permanently removed 
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      if (store!=null) {
        try {
          store.close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }
}