# Log Analytics workspace backing Container Insights (below, in aks.tf). Holds
# container/pod/node CPU+memory metrics and logs, queryable via KQL and
# rendered as time-series dashboards in the AKS resource's "Insights" blade.
resource "azurerm_log_analytics_workspace" "this" {
  name                = "${var.prefix}-logs-${random_string.suffix.result}"
  resource_group_name = azurerm_resource_group.this.name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = var.tags
}
