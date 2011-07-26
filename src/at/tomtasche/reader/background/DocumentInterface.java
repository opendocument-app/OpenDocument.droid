
package at.tomtasche.reader.background;

import java.util.List;

public interface DocumentInterface {

    /**
     * @return True if there's a next page.
     */
    public boolean hasNext();

    /**
     * Prepares the next page in background and displays it as soon as it's
     * ready.
     */
    public void getNext();
    
    public void loadPage(int i);

    /**
     * @return True if there's a previous page.
     */
    public boolean hasPrevious();

    /**
     * Prepares the previous page in background and displays it as soon as it's
     * ready.
     */
    public void getPrevious();

    /**
     * @return The number of available pages in this document.
     */
    public int getPageCount();

    /**
     * @return The index of the currently displayed page.
     */
    public int getPageIndex();
    
    /**
     * @return The names of available pages in this document..
     */
    public List<String> getPageNames();
    
    public List<String> getAll();

}
