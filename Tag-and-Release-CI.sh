#!/bin/bash

# this script is intended to be run non-interactively, in the context of a Jenkins job, primarily 
# because it uses environment variables related to commit SHAs and creds and stuff

# color functions
function blue {
  echo -e "\e[34m${1}\e[0m"
}
function red {
  echo -e "\e[31m${1}\e[0m"
}
function green {
  echo -e "\e[32m${1}\e[0m"
}

function banner {
  echo "
      ███████╗██████╗░███████╗  ██████╗░███████╗██╗░░░░░███████╗░█████╗░░██████╗███████╗
      ██╔════╝██╔══██╗██╔════╝  ██╔══██╗██╔════╝██║░░░░░██╔════╝██╔══██╗██╔════╝██╔════╝
      █████╗░░██████╔╝█████╗░░  ██████╔╝█████╗░░██║░░░░░█████╗░░███████║╚█████╗░█████╗░░
      ██╔══╝░░██╔═══╝░██╔══╝░░  ██╔══██╗██╔══╝░░██║░░░░░██╔══╝░░██╔══██║░╚═══██╗██╔══╝░░
      ███████╗██║░░░░░██║░░░░░  ██║░░██║███████╗███████╗███████╗██║░░██║██████╔╝███████╗
      ╚══════╝╚═╝░░░░░╚═╝░░░░░  ╚═╝░░╚═╝╚══════╝╚══════╝╚══════╝╚═╝░░╚═╝╚═════╝░╚══════╝
"
  echo

  echo "
    WARNING ----------------------------------------------------------------------------------------------- WARNING

    This utility is not smart enough to understand when you're doing something dangerous, PLEASE BE CAREFUL!

    WARNING ----------------------------------------------------------------------------------------------- WARNING
    
    "
}

# if any of the env vars we need aren't set, report them and exit
function check_vars_present {
  required_vars=("GIT_URL" "GIT_COMMIT" "GIT_USER" "GIT_TOKEN")
  missing_vars=0
  for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
      red "\$${var} is not set"
      (( missing_vars += 1 ))
    fi
  done
  if [ $missing_vars -gt 0 ]; then
    exit $missing_vars
  fi 
}

# exits with a message if GIT_URL isn't an https address
# otherwise, sets the following variables (examples based on 'https://github.sys.cigna.com/cigna/enterprise-pipeline-framework.git')
#  - git_host - hostname, e.g. github.sys.cigna.com
#  - git_org - organization, e.g. cigna
#  - git_repo - repo name, e.g. enterprise-pipeline-framework (no .git)
function parse_git_url {
  if [[ "$GIT_URL" != https://* ]]; then
    red "Expecting \$GIT_URL to be an https address, got '$GIT_URL'"
    exit $ERROR_EXIT
  fi
  local remainder=${GIT_URL#*://}
  git_host=${remainder%%/*}
  remainder=${remainder#*/}
  git_org=${remainder%%/*}
  remainder=${remainder#*/}
  git_repo=${remainder/%.git/}
  blue "Incoming git URL: $GIT_URL"
}

function configure_git {
  remoteUri="https://${GIT_USER}:${GIT_TOKEN}@${GIT_URL#*//}"
  git remote set-url origin "$remoteUri"
  git config user.email DevOpsSystems@Cigna.com
  git config user.name DevOpsSystems
  git config --global push.default simple
  blue "Configured Git"
}

function clean_tags {
  blue "Fetching tags"
  set -e
  git fetch --prune --prune-tags --tags origin 2> /dev/null
  set +e
}

function change_tag {
  local VERSION_NUM_TAG=$1
  local ROLLING_TAG=$2
  
  if git rev-parse -q --verify "refs/tags/$VERSION_NUM_TAG" >/dev/null; then
    red "Tag $VERSION_FROM_POM already exists. Please increment the version in pom.xml before proceeding"
    exit $ERROR_EXIT
  fi
  
  blue "Creating/moving tags"
  # if creating the tags fails, we should bail
  set -e
  # log what actually runs
  set -x
  # create and push the version number tag
  git tag "$VERSION_NUM_TAG" "$GIT_COMMIT"
  git push origin "refs/tags/$VERSION_NUM_TAG"
  set +x
  green "Created tag $VERSION_NUM_TAG"
  
  set -x
  # create and push (with force) the 'rolling tag'
  git tag -af -m "$VERSION_NUM_TAG Release" "$ROLLING_TAG"  "$GIT_COMMIT"
  git push -f origin "refs/tags/$ROLLING_TAG"
  set +x
  green "Moved tag $ROLLING_TAG"
  set +e
}

function set_release_notes {
  # read notes between markers - increment header counter whenever an H2 (##) is encountered,
  # and only return the line if headerCounter == 1, meaning we get exactly every line of the first H2
  RELEASE_NOTES=$(awk '/^##[^#]/{ headerCounter++ } headerCounter == 1' CHANGELOG.md)
}

function create_github_release {
  blue "Creating release on GitHub"
  
  set_release_notes

  blue "Release notes from changelog file:"
  echo "$RELEASE_NOTES"

  #check if releases notes is a valid json or default it
  if ! echo "$RELEASE_NOTES" | jq -Rs . > /dev/null; then
    red "Failed to parse RELEASE_NOTES, defaulting it to 'Please update release notes manually directly in the repository as it cannot be extracted from CHANGELOG.md'"
    RELEASE_NOTES='Please update release notes manually directly in the repository as it cannot be extracted from CHANGELOG.md'
  fi
  
  RELEASE_API_JSON=$(cat <<-END
{
"tag_name": "${VERSION_FROM_POM}",
"target_commitish": "${GIT_COMMIT}",
"name": "${VERSION_FROM_POM}",
"body": $(echo "$RELEASE_NOTES" | jq -Rs .),
"draft": false,
"prerelease": false
}
END
  )

  response=$(curl -L "https://$git_host/api/v3/repos/$git_org/$git_repo/releases" \
   -s -w "%{http_code}" \
   -H "Authorization: Bearer $GIT_TOKEN" \
   -H "Accept: application/vnd.github+json" \
   -H "Content-Type: text/plain" \
   --data "$RELEASE_API_JSON")


  http_code=$(tail -n1 <<< "$response")  # get the last line
  content=$(sed '$ d' <<< "$response")   # get all but the last line which contains the status code

  if [ "$http_code" -ne 201 ]
  then
    blue "GitHub API request body:"
    echo "$RELEASE_API_JSON"
    red "Response HTTP code: $http_code"
    red "GitHub API response:"
    echo "$content"
        
    red "Unable to create GitHub release for tag $VERSION_FROM_POM. Please fix the issue and create the release manually."
    exit $ERROR_EXIT
  fi

  green "Created release in GitHub for version $VERSION_FROM_POM"
}

function set_maven_version {
  # note that $? needs to check the return val of the maven call, don't put anything in between
  VERSION_FROM_POM=$(mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.version -q -DforceStdout)
  if [ $? -ne 0 ] || [ -z "$VERSION_FROM_POM" ]; then
    red "Looks like there was a problem getting the version from the pom file."
    red "Maven output:"
    echo "$VERSION_FROM_POM"
    return $ERROR_EXIT
  fi
  blue "Version from maven: $VERSION_FROM_POM"
}

function release_version {

  clean_tags
  # if getting the maven version failed, then exit with an error
  if ! set_maven_version; then
    exit $ERROR_EXIT
  fi

  # create version number tag and move rolling tag
  change_tag "$VERSION_FROM_POM" "$ROLLING_TAG_NAME"

  create_github_release
}

# get version from maven, check if the tag exists, and create a commit status
# commit status is bad if tag already exists, otherwise good
function run_precheck {
  # we don't exit from here because this is just a check, we don't want to fail the pipeline
  local check_name="POM version check"
  
  clean_tags
  local state
  local descr
  
  # if we get an error from getting the maven version, that's one error
  if ! set_maven_version; then
    state='failure'
    descr="Problem getting version from maven."
  # if we get the version and its tag exists, that's a different error
  elif git rev-parse -q --verify "refs/tags/$VERSION_FROM_POM" >/dev/null; then
    red "Tag $VERSION_FROM_POM already exists, will post failing commit status."
    state='failure'
    descr="Tag $VERSION_FROM_POM already exists."
  # if we get the version and its tag doesn't exist, we're good
  else
    state='success'
    descr="Tag $VERSION_FROM_POM does not exist yet."
  fi
  local request_body
  request_body=$(jq -n --arg state "$state" --arg description "$descr" --arg context "$check_name" '$ARGS.named')
  local response
  response=$(curl -L "https://$git_host/api/v3/repos/$git_org/$git_repo/statuses/$GIT_COMMIT" \
     -s -w "%{http_code}" \
     -H "Authorization: Bearer $GIT_TOKEN" \
     -H "Accept: application/vnd.github+json" \
     -H "Content-Type: application/json" \
     --data "$request_body")
       
  local http_code=$(tail -n1 <<< "$response")  # get the last line
  local content=$(sed '$ d' <<< "$response")   # get all but the last line which contains the status code

  if [ "$http_code" -ne 201 ]
  then
    blue "GitHub API request body:"
    echo "$request_body"
    red "Response HTTP code: $http_code"
    red "GitHub API response:"
    echo "$content"
  fi    
}

## main function of script starts here

# a constant exit code for any problem
ERROR_EXIT=1

# the name of the tag to use as a 'rolling tag'
ROLLING_TAG_NAME="release"

banner

# check that we have the env vars we need to do the work
check_vars_present

# pull out the components of GIT_URL
parse_git_url

# add user/token and name/email to git setup
configure_git

case $1 in
  'release')
  # create/push tags and make a release in GitHub
  release_version
  ;;
  'precheck')
  run_precheck
  ;;
  *)
  red "No subcommand provided. Valid subcommands are 'release' and 'precheck'."
  exit $ERROR_EXIT
  ;;
esac
