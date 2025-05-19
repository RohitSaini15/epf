findFiles() {
    echo 'Searching for file(s)...'
    mc_output=$(mc find "minio/$BUCKET_NAME/$FOLDER_NAME" --name "$FILESPATTERN" --exec mc cp ./ 2>&1 1>/dev/null)
    echo 'Moving file(s) into workspace...'
    if [[ "$mc_output" != "" ]]; then
        if [[ $(echo "$mc_output" | grep -c 'validate source') -eq 1 ]]; then
            echo 'Folder is probably empty...'
        elif [[ $(echo "$mc_output" | grep -c 'removeManager') -eq 1 ]]; then
            echo 'This error probably does not matter...'
        else
            echo "Move failed - findFiles: '$mc_output'"
            exit 1
        fi
    fi
}
