# Gmail Google account Implementation Plan

- Register the Android package and signer using [Personal mode (Android)](https://console.cloud.google.com/auth/overview/personal?apis=ws,wsmcp,tasksmcp&clientType=android&isAgent=true). Google's [setup guide](https://developers.google.com/workspace/gmail/api/guides/configure-mcp-server) requires Google Workspace Developer Preview access, Gmail API and Gmail MCP API enabled in the project, and **Use this client for an AI-powered agent**. Console access is currently blocked by Google's two-step verification requirement.
- Write repository tests proving that discovery without a successful authenticated read cannot enable Gmail, and that legacy manual settings do not count as a Google connection.
- Use Google Identity Services to select an account and request read-only permission, then verify `list_labels` on the fixed Gmail MCP endpoint. Expose only supported read tools. Do not offer a manual token form for Gmail.
- Build and export; mark Gmail end-to-end unverified until a real Google account authorizes and the read check succeeds on-device.
