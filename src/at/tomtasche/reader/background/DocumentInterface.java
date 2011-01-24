package at.tomtasche.reader.background;

public interface DocumentInterface {
    
    /**
     * Prepares the next page in background and displays it as soon as it's ready.
     * 
     * @return True if there's a next page.
     */
    public boolean getNext();
    
    /**
     * Prepares the previous page in background and displays it as soon as it's ready.
     * 
     * @return True if there's a previous page.
     */
    public boolean getPrevious();
    
    /**
     * 
     * @return The number of available pages in this document.
     */
    public int getPageCount();
    
    /**
     * 
     * @return The index of the currently displayed page.
     */
    public int getPageIndex();

}
