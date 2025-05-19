move() {
  local
    echo 'Moving code into phase cache...'
    mc_output=$(mc mirror --quiet --overwrite --insecure ./ "minio/$BUCKET_NAME/$FOLDER_NAME/" 2>&1 1>/dev/null)
    if [[ "$mc_output" != "" ]]; then
        if [[ $(echo "$mc_output" | grep -c 'validate source') -eq 1 ]]; then
            echo 'Folder is probably empty...'
        elif [[ $(echo "$mc_output" | grep -c 'removeManager') -eq 1 ]]; then
            echo 'This error probably does not matter...'
        else
            echo "Move failed - moveFilesEnd: '$mc_output'"
            exit 1
        fi
    fi
}
