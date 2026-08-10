# One ECR repo per image. The EKS managed node group's role already carries
# AmazonEC2ContainerRegistryReadOnly, so nodes can pull without pull secrets.
resource "aws_ecr_repository" "this" {
  for_each = toset(["lilac-planner-backend", "lilac-planner-frontend"])

  name                 = each.key
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}
