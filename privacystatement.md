# Duix SDK Privacy Statement

**Last Updated:** 2025/06/09  
**SDK Name:** Duix Real-time Interaction SDK  
**Provider:** Duix.com Platform 

## 1. Introduction  
This Privacy Statement explains how [Duix.com] ("we," "us," or "our") processes user data through our open-source SDK when integrated into third-party applications ("Customer Apps"). The SDK enables real-time digital human interaction capabilities (voice/text).  

**Key Notes for Developers:**  
- **Compliance Responsibility:** Customers integrating this SDK must comply with applicable privacy laws and disclose our SDK in their privacy policy.  
- **Consent Requirement:** Customers must obtain valid user consent before initializing the SDK or collecting data.  
- **Minimal Data Collection:** We collect only essential data for core functionality and operational stability.  

---

## 2. Data Collection & Purpose  

### A. Core Functionality Data  
| **Data Type**          | **Purpose**                                                                 |
|-------------------------|-----------------------------------------------------------------------------|
| Audio/Text Input        | Process user queries to enable real-time digital human interactions.        |
| Interaction Metadata    | Maintain session stability (e.g., timestamps, session IDs).                  |

### B. Operational Data  
| **Data Type**               | **Purpose**                                                                 | **Retention**       |
|-----------------------------|-----------------------------------------------------------------------------|---------------------|
| Device Information<br>(e.g., OS) | Monitor SDK performance, diagnose errors, and improve reliability.          | 6 months            |
| Anonymized Usage Metrics<br>(e.g., feature adoption rates) | Optimize resource allocation and service quality.                            | Aggregated indefinitely |

**Note:**  
- Audio/text data is processed in real-time and **not stored** after session completion.  

---

## 3. Permissions & Controls  
### Required Permissions  
| **Permission**         | **Platforms**       | **Purpose**                                                               |
|------------------------|---------------------|---------------------------------------------------------------------------|
| Microphone Access      | Android, iOS, Web   | Capture voice input for real-time interactions.                          |
| Network State          | Android, iOS, Web   | Ensure stable connectivity during sessions.                               |

### Optional Permissions  
| **Permission**         | **Purpose**                                                                 |
|------------------------|-----------------------------------------------------------------------------|
| Camera Access          | Enable video simulation features (disabled by default).                    |
| Wake Lock              | Prevent device sleep during active sessions.                                |

**Developer Obligations:**  
- **Consent Layers:** Implement granular consent for permissions (e.g., separate toggles for microphone/camera).  
- **Delayed Initialization:** Initialize the SDK only after obtaining user consent.  
- **Configuration:** Disable unused features (e.g., camera) via SDK parameters.  

---

## 4. Data Sharing 
- **Third-Party Processors:** Audio/text data may be sent to AI models designated by the Customer App. We do not control these processors.  
- **No Cross-Context Advertising:** We never sell user data.  

---

## 5. User Rights & Compliance  
### A. User Rights  
Users may:  
- Access, correct, or delete their data.  
- Withdraw consent or object to processing.  
- Request data portability.  

**Fulfillment Process:**  
1. Users must contact the **Customer App developer** to exercise rights.  
2. We provide APIs to support data deletion/access requests upon developer request.  

### B. Children’s Privacy  
- The SDK is **not intended for users under 13** (or 16 in some regions).  
- Customers must implement age-gating and obtain parental consent for child users.  

---

## 6. Security Measures  
- **Encryption:** Data in transit (TLS 1.3+) . 

---

## 7. Policy Updates  
- Changes will be communicated via GitHub repository releases and version tags.  
- Customers must update integrated SDK versions to reflect changes.  

---

## 8. Contact Us  
For privacy inquiries:  
- **Developers:** Open issues in our GitHub Repository：[https://github.com/duixcom].  
- **End-Users:** Contact the Customer App’s support team.  
- **Legal Requests:** support@duix.com

**DPO Contact:** support@duix.com 