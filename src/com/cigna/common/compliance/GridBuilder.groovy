package com.cigna.common.compliance

import com.cloudbees.groovy.cps.NonCPS

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * This class is used to create grid data with the defined size and topics. With automated word wrapping along with
 * the ability to create grids of any kind.
 */

@SuppressWarnings(['DuplicateStringLiteral', 'DuplicateNumberLiteral'])
class GridBuilder implements Serializable {

    /**
     Creates a new grid buffer for use between grids. For example wanting separation between a state grid, and an error
     grid

     @param width The width of the given grid
     @return A formatted string containing buffer
     */
    static String formatGridBuffer(Integer width) {
        StringBuffer x = new StringBuffer()
        Integer border = width - 2
        x << ''.center(width, '-') << '\n'
        x << '|' << ''.center(border) << '|\n'
        x << ''.center(width, '-') << '\n'
        x
    }

    /**
     Wraps a message to be contained within the provided width.
     grid

     @param message The message to be wrapped
     @param width The width of the given row
     @return A List[String] of the desired width
     */
    final static String STX = '\u001B\\[8m'
    final static String ETX = '\u001B\\[0m'
    final static String ENCODED_PATTERN = ".*(${STX}.*${ETX})\\(Click Link\\).*"
    final static String TARGET_VALUE = '(Click Link)'

    static List<String> wordWrap(
        String input,
        int lineWidth = 70,
        Closure<List<Integer>> transformer = lengthFromURLTarget()
    ) {
        if (input == null || lineWidth <= 1) {
            return ['']
        }

        List<String> output = []
        String line = ''

        int sz, skip
        if (input.length() > lineWidth) {
            (skip, sz) = transformer(input)
            if (sz <= lineWidth) {
                // the downstream logic can't handle the encoded url so we handle it here
                String padding = ( ' ' * ( ( lineWidth - sz ) / 2 ) )
                String padding1 = ( ' ' * ( ( lineWidth - sz ) / 2 + ( sz % 2 ) ) )
                return [padding + input + padding1]
            }
            line += input[0..skip]
            input[( skip + 1 )..-1].split(' ').each { word ->
                if (( line.size() + word.length() + 1 ) <= lineWidth) {
                    line <<= word + ' '
                } else {
                    output += line
                    line = word + ' '
                }
            }
            output += line
            output
        } else {
            [input]
        }
    }

    @NonCPS
    static Closure<List<Integer>> lengthFromURLTarget() {
        { String inputStr ->
            Pattern p = Pattern.compile(ENCODED_PATTERN)
            Matcher m = p.matcher(inputStr)
            if (m.matches()) {
                int length = m.group(1).length()
                [length, inputStr.length() - length]
            } else {
                [0, inputStr.length()]
            }
        }
    }

    /**
     Wraps a row of messages, split by the given width. E.g. width/row.size()

     @param row The List[String] messages to be wrapped
     @param width The width of the given row
     @return A WrappedRow of the desired width
     */

    static WrappedRow rowWrap(List<String> row, Integer width) {
        Integer cellWidth = Math.floorDiv(width, row.size())
        List<WrappedCell> wrappedCells = []

        row.each {
            List<String> wrappedCell = wordWrap(it, cellWidth)
            wrappedCells += new WrappedCell(wrappedCell, cellWidth)
        }

        new WrappedRow(wrappedCells, width)
    }

    /**
     Wraps a row of messages with keys, split by the given width. E.g. width/row.size()

     @param row The List[String] messages to be wrapped
     @param width The width of the given row
     @return A WrappedRow of the desired width
     */

    static WrappedRow rowWrap(List<Map<String, Object>> row) {
        List<WrappedCell> wrappedCells = []
        Integer width = 0

        row.each { cell ->
            Integer cellWidth = ( cell['Width'] ?: '30' ).toString().toInteger()
            List<String> wrappedCell = wordWrap(
                ( cell['Data'] ?: cell['Topic'] ?: '' ).toString(),
                cellWidth,
            )
            wrappedCells += new WrappedCell(wrappedCell, cellWidth)
            width += cellWidth
        }

        new WrappedRow(wrappedCells, width)
    }

    /**
     Creates a new grid single message grid, wrapped at the given width.

     @param message The message to be wrapped
     @param width The width of the given grid
     @return A formatted string containing wrapped message grid
     */

    static String formatMessageGrid(String message, Integer width) {
        StringBuffer x = new StringBuffer()
        Integer border = width - 2
        List<String> messageList = wordWrap(message, border)
        formatGridBorder(x, width)
        messageList.each {
            x << '|' << "${it}".center(border) << '|\n'
        }
        formatGridBorder(x, width)
    }

    /**
     Adds a grid line containing only dashes to the given buffer

     @param buffer The buffer to add the grid line to
     @param width The width of the given grid
     */
    static String formatGridBorder(StringBuffer buffer, Integer width) {
        buffer << ''.center(width, '-') << '\n'
    }

    /**
     Creates a grid of given topics, wrapped for the given grid

     @param topics The list of topics for a grid
     @param width The width of the grid
     @return A formatted string containing wrapped topic grid
     */

    static String formatTopicGrid(List<String> topics, Integer width) {
        StringBuffer buffer = new StringBuffer()
        Integer border = width - ( 2 + topics.size() - 1 )
        formatGridBorder(buffer, width)

        WrappedRow topicsWrapped = rowWrap(topics, border)

        for (Integer i = 0; i < topicsWrapped.largest; i++) {
            topicsWrapped.data.each { item ->
                formatRowItem(buffer, item, i)
            }
            buffer << '|\n'
        }
        formatGridBorder(buffer, width)
        buffer
    }

    /**
     Creates a grid of given topics, wrapped for the given grid

     @param topics The list[Map[String:Object]] of topics for a grid, with options if necessary.
      Map keys are ["Topic": "topic", "Width": 123] If the given topic doesn't contain a width,
      it will be divided evenly by the given width. E.g. width/NoWidthTopics
     @param width The width of the grid
     @return A formatted string containing wrapped topic grid with defined sizes
     */

    static String formatTopicGridWithOptions(List<Map<String, Object>> topics, Integer width) {
        StringBuffer buffer = new StringBuffer()
        Integer border = width - ( 2 + topics.size() - 1 )
        formatGridBorder(buffer, width)

        List<Integer> idxMods = []
        Integer running = border
        topics.eachWithIndex { it, idx ->
            if (!it.containsKey('Width')) {
                idxMods += idx
                return
            }
            running -= it['Width']
        }
        idxMods.each {
            topics[it]['Width'] = Math.floorDiv(running, idxMods.size())
        }

        WrappedRow topicsWrapped = rowWrap(topics)

        for (Integer i = 0; i < topicsWrapped.largest; i++) {
            StringBuffer rowString = new StringBuffer()
            topicsWrapped.data.each { item ->
                formatRowItem(rowString, item, i)
            }
            if (rowString.size() < width - 1) {
                rowString << ( ' '.center(rowString.size() - ( width - 1 )) )
            }
            buffer << rowString.toString() + '|\n'
        }
        formatGridBorder(buffer, width)
        buffer
    }

    /**
     Creates a grid of data

     @param data The List[List[String]] of data for a grid
     @param width The width of the grid
     @return A formatted string containing wrapped data grid
     */
    static String formatDataGrid(List<List<String>> data, Integer width) {
        if (data.size() == 0) {
            return ''
        }
        StringBuffer buffer = new StringBuffer()
        Integer border = width - ( 2 + data.size() - 1 )
        formatGridBorder(buffer, width)
        List<WrappedRow> wrappedRows = []

        data.each {
            wrappedRows += rowWrap(it, border)
        }
        wrappedRows.each { row ->
            StringBuffer rowString = new StringBuffer()
            for (Integer i = 0; i < row.largest; i++) {
                row.data.each { item ->
                    formatRowItem(rowString, item, i)
                }
                if (rowString.size() < width - 1) {
                    rowString << ( ' '.center(rowString.size() - ( width - 1 )) )
                }
                buffer << rowString.toString() + '|\n'
            }
            buffer << ''.center(width, '-') << '\n'
        }
        buffer
    }

    /**
     Creates a grid of data

     @param data The List[Map[String:String]] of data for a grid
     @param keyOrder The List[String] keys in order of display for the grid. E.g. ["Key2", "Key1", "Key3"]
     @param width The width of the grid
     @return A formatted string containing wrapped data grid in defined order
     */
    @SuppressWarnings(['NestedForLoop'])
    static String formatDataGrid(List<Map<String, String>> data, List<String> keyOrder, Integer width) {
        if (data.size() == 0) {
            return ''
        }
        StringBuffer buffer = new StringBuffer()
        List<String> tmpKeyOdr = keyOrder
        tmpKeyOdr = keyOrder ?: data[0].keySet()
        Integer border = width - ( 2 + keyOrder.size() - 1 )
        formatGridBorder(buffer, width)
        List<WrappedRow> wrappedRows = []

        data.each { Map<String, String> row ->
            Integer cellWidth = Math.floorDiv(border, row.size())
            List<WrappedCell> wrappedCells = []
            for (Integer i = 0; i < row.size(); i++) {
                wrappedCells += new WrappedCell(
                    wordWrap((String) row[tmpKeyOdr[i]], cellWidth.toInteger()), cellWidth, tmpKeyOdr[i]
                )
            }
            wrappedRows += new WrappedRow(wrappedCells, border)
        }
        formatBuffer(wrappedRows, tmpKeyOdr, width, buffer)
        buffer
    }

    @SuppressWarnings(['NestedForLoop'])
    static List<WrappedRow> formatBuffer(
        List<WrappedRow> wrappedRows,
        List<String> tmpKeyOdr,
        int width,
        StringBuffer buffer) {
        wrappedRows.each {
            for (Integer i = 0; i < it.largest; i++) {
                StringBuffer rowString = new StringBuffer()
                for (Integer j = 0; j < tmpKeyOdr.size(); j++) {
                    it.getCellForKey(tmpKeyOdr[j]).each { item ->
                        formatRowItem(rowString, item, i)
                    }
                }
                if (rowString.size() < width - 1) {
                    rowString << ( ' '.center(rowString.size() - ( width - 1 )) )
                }
                buffer << rowString.toString() + '|\n'
            }
            buffer << ''.center(width, '-') << '\n'
        }
    }

    static StringBuffer formatRowItem(StringBuffer rowString, WrappedCell item, int i) {
        if (item.data[i].length() >= item.width) {
            rowString << '|' + item.data[i]
        } else {
            rowString << '|' + item.data[i].center(item.width)
        }
    }

    /**
     Creates a grid of data

     @param data The List[Map[String:String]] of data for a grid
     @param keyOptions The Map[String:Integer] keys in order of display for the grid with sizes.
      E.g. ["Key2": -1, "Key1": 42, "Key3": 30] Any keys with length '-1' will get split in remaining size
      from defined keys.
     @param width The width of the grid
     @return A formatted string containing wrapped data grid in defined order
     */
    @SuppressWarnings(['NestedForLoop'])
    static String formatDataGrid(List<Map<String, Object>> data, Map<String, Integer> keyOptions, Integer width) {
        if (data.size() == 0) {
            return ''
        }
        StringBuffer buffer = new StringBuffer()
        Integer border = width - ( 2 + keyOptions.size() - 1 )
        formatGridBorder(buffer, width)
        List<WrappedRow> wrappedRows = []
        List<String> keyOrder = keyOptions.keySet() as List<String>

        List<String> idxMods = []
        Integer running = border
        keyOrder.eachWithIndex { it, idx ->
            if (keyOptions[it] == -1) {
                idxMods += it
                return
            }
            running -= keyOptions[it]
        }
        idxMods.each {
            keyOptions[it] = Math.floorDiv(running, idxMods.size())
        }

        data.each { row ->
            List<WrappedCell> wrappedCells = []
            for (Integer i = 0; i < row.size(); i++) {
                wrappedCells += new WrappedCell(
                    wordWrap((String) row[keyOrder[i]], keyOptions[keyOrder[i]]),
                    keyOptions[keyOrder[i]],
                    keyOrder[i]
                )
            }
            wrappedRows += new WrappedRow(wrappedCells, border)
        }
        formatBuffer(wrappedRows, keyOrder, width, buffer)
        buffer
    }
}

class WrappedRow implements Serializable {

    protected List<WrappedCell> data
    protected Integer width
    protected Integer length
    protected Integer largest

    WrappedCell getCellForKey(String key) {
        WrappedCell ret = null
        this.data.each {
            if (it.key == key) {
                ret = it
            }
        }
        ret
    }

    WrappedRow(List<WrappedCell> data, Integer width) {
        this.data = []
        this.largest = 0
        data.each {
            if (!( this.largest > it.length )) {
                this.largest = it.length
            }
        }
        data.each {
            while (it.length < this.largest) {
                it.data += ''
                it.length += 1
            }
            this.data += it
        }
        this.width = width
        this.length = this.data.size()
    }
}

class WrappedCell implements Serializable {

    protected List<String> data
    protected Integer width
    protected Integer length
    protected String key

    WrappedCell(List<String> data, Integer width, String key = null) {
        this.data = data
        this.width = width
        this.length = data.size()
        this.key = key
    }
}
