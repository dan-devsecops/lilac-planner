module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.24"

  cluster_name    = "${var.prefix}-eks"
  cluster_version = var.kubernetes_version

  cluster_endpoint_public_access = true
  enable_irsa                    = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    default = {
      instance_types = [var.node_instance_type]
      min_size       = 2
      max_size       = 4
      desired_size   = var.node_count
    }
  }

  # The identity running terraform becomes a cluster admin.
  enable_cluster_creator_admin_permissions = true

  # Grant the CI role cluster-admin so GitHub Actions can helm-deploy.
  access_entries = var.enable_github_oidc ? {
    github_ci = {
      principal_arn = aws_iam_role.github[0].arn
      policy_associations = {
        admin = {
          policy_arn = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
          access_scope = {
            type = "cluster"
          }
        }
      }
    }
  } : {}

  tags = var.tags
}
